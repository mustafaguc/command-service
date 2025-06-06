package com.powersoft.commandservice.service

import com.powersoft.commandservice.model.*
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
        return jobRepository.findLogsByJobId(jobId)
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
                val log = commandExecutorService.executeCommand(job.id, index, command)
                jobRepository.saveLog(log)
                
                // If the command failed, stop execution
                if (log.exitCode != 0) {
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
            
            // All commands executed successfully
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