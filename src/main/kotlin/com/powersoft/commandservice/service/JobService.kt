package com.powersoft.commandservice.service

import com.powersoft.commandservice.model.*
import com.powersoft.commandservice.model.CommandStatus
import com.powersoft.commandservice.repository.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Service for managing jobs and executing commands
 */
@Service
class JobService(
    private val jobRepository: JobRepository,
    private val commandExecutorService: CommandExecutorService,
    private val restClient: RestClient
) {
    private val logger = LoggerFactory.getLogger(JobService::class.java)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * Creates a new job with the given commands
     * @param commandRequest The command request containing commands and webhook URL
     * @return The created job
     */
    fun createJob(commandRequest: CommandRequest): Job {
        val jobId = UUID.randomUUID().toString()
        
        val job = Job(
            id = jobId,
            commands = commandRequest.commands,
            webhookUrl = commandRequest.webhookUrl
        )
        
        // Save the job
        val savedJob = jobRepository.save(job)
        
        // Execute the job asynchronously
        executor.submit { executeJob(savedJob) }

        return savedJob
    }

    /**
     * Gets a job by its ID
     * @param jobId The job ID
     * @return The job, or null if not found
     */
    fun getJob(jobId: String): Job? {
        return jobRepository.findById(jobId)
    }

    /**
     * Gets logs for a job
     * @param jobId The job ID
     * @return The logs for the job
     */
    fun getJobLogs(jobId: String): List<CommandLog> {
        val logs = jobRepository.findLogsByJobId(jobId)
        logger.info("Retrieved ${logs.size} logs for job $jobId")
        
        // If no logs were found, log a warning
        if (logs.isEmpty()) {
            logger.warn("No logs found for job $jobId")
        } else {
            // Log the first few logs for debugging
            logs.take(2).forEachIndexed { index, log ->
                logger.debug("Log $index for job $jobId: command=${log.command.command}, exitCode=${log.exitCode}, outputLength=${log.output.length}")
            }
        }
        
        return logs
    }
    
    /**
     * Cancels a job by its ID
     * @param jobId The job ID
     * @return The cancelled job, or null if not found or already completed
     */
    fun cancelJob(jobId: String): Job? {
        val job = jobRepository.findById(jobId) ?: return null
        
        // Check if the job can be cancelled (only PENDING or RUNNING jobs can be cancelled)
        if (job.status != JobStatus.PENDING && job.status != JobStatus.RUNNING) {
            logger.warn("Cannot cancel job $jobId with status ${job.status}")
            return null
        }
        
        // Cancel all running processes for this job
        val processesCancelled = commandExecutorService.cancelJob(jobId)
        
        // Update job status to CANCELLED
        val cancelledJob = job.copy(
            status = JobStatus.CANCELLED,
            completedAt = LocalDateTime.now()
        )
        jobRepository.update(cancelledJob)
        
        // Log the cancellation
        if (processesCancelled) {
            logger.info("Job $jobId cancelled with running processes terminated")
        } else {
            logger.info("Job $jobId cancelled, no running processes found")
        }
        
        // Call webhook if provided
        job.webhookUrl?.let { callWebhook(cancelledJob) }
        
        return cancelledJob
    }

    /**
     * Executes a job by running its commands sequentially
     * @param job The job to execute
     */
    private fun executeJob(job: Job) {
        try {
            // Update job status to RUNNING
            val startedJob = job.copy(
                status = JobStatus.RUNNING,
                startedAt = LocalDateTime.now()
            )
            jobRepository.update(startedJob)
            
            // Execute each command sequentially
            for ((index, command) in job.commands.withIndex()) {
                // Create a partial log with empty output to start with
                val startTime = LocalDateTime.now()
                val currentOutput = StringBuilder()
                
                // Create a callback to handle streaming output
                val outputCallback = { line: String ->
                    // Append the new line to our current output
                    currentOutput.append(line).append("\n")
                    
                    // Create a partial log with the current output
                    val partialLog = CommandLog(
                        jobId = job.id,
                        commandIndex = index,
                        command = command,
                        output = currentOutput.toString(),
                        exitCode = -999, // Special code indicating command is still running
                        status = CommandStatus.RUNNING,
                        startTime = startTime,
                        endTime = LocalDateTime.now() // Current time as temporary end time
                    )
                    
                    // Save the partial log
                    jobRepository.saveLog(partialLog)
                }
                
                // Execute the command with the streaming callback
                val log = commandExecutorService.executeCommand(job.id, index, command, outputCallback)
                
                // Save the final log
                logger.info("Saving final log for job ${job.id}, command index $index: ${command.command}")
                jobRepository.saveLog(log)
                logger.debug("Log saved for job ${job.id}, command index $index, output length: ${log.output.length}")
                
                // Check the command status
                when (log.effectiveStatus) {
                    CommandStatus.SUCCESS -> {
                        // Command executed successfully, continue to next command
                        logger.info("Command executed successfully: ${command.command}")
                    }
                    CommandStatus.HARMFUL -> {
                        // Command was rejected as harmful, but we continue execution
                        logger.warn("Harmful command skipped, continuing with next command: ${command.command}")
                    }
                    else -> {
                        // Command failed for other reasons, stop execution
                        val failedJob = startedJob.copy(
                            status = JobStatus.FAILED,
                            completedAt = LocalDateTime.now()
                        )
                        jobRepository.update(failedJob)
                        
                        // Call webhook if provided
                        job.webhookUrl?.let { callWebhook(failedJob) }
                        
                        return
                    }
                }
            }
            
            // All commands executed successfully or were skipped as harmful
            val completedJob = startedJob.copy(
                status = JobStatus.COMPLETED,
                completedAt = LocalDateTime.now()
            )
            jobRepository.update(completedJob)
            
            // Call webhook if provided
            job.webhookUrl?.let { callWebhook(completedJob) }
        } catch (e: Exception) {
            logger.error("Error executing job ${job.id}", e)
            
            // Update job status to FAILED
            val failedJob = job.copy(
                status = JobStatus.FAILED,
                startedAt = job.startedAt ?: LocalDateTime.now(),
                completedAt = LocalDateTime.now()
            )
            jobRepository.update(failedJob)
            
            // Call webhook if provided
            job.webhookUrl?.let { callWebhook(failedJob) }
        }
    }

    /**
     * Calls a webhook with the job status
     * @param job The job
     */
    private fun callWebhook(job: Job) {
        try {
            val webhookResponse = JobResponse(
                jobId = job.id,
                status = job.status,
                createdAt = job.createdAt,
                startedAt = job.startedAt,
                completedAt = job.completedAt
            )
            
            job.webhookUrl?.let {
                restClient.post()
                    .uri(it)
                    .body(webhookResponse)
                    .retrieve()
                    .toBodilessEntity()
            }
        } catch (e: Exception) {
            logger.error("Error calling webhook for job ${job.id}", e)
        }
    }
}