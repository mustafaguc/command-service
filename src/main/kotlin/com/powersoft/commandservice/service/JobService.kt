package com.powersoft.commandservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.powersoft.commandservice.model.*
import com.powersoft.commandservice.repository.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executors

/**
 * Service for managing jobs and executing commands
 */
@Service
class JobService(
    private val repository: JobRepository,
    private val commandExecutorService: CommandExecutorService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(JobService::class.java)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val webClient: WebClient = WebClient.create()

    /**
     * Creates a new job with the given commands
     * @param commandRequest The command request containing commands and webhook URL
     * @return The created job
     */
    fun createJob(commandRequest: CommandRequest) =
        Job(UUID.randomUUID().toString(), commandRequest.commands, commandRequest.webhookUrl)
            .let(repository::save)
            .also { executor.submit { executeJob(it) } }

    /**
     * Gets a job by its ID
     * @param jobId The job ID
     * @return The job, or null if not found
     */
    fun getJob(jobId: String): Job? {
        return repository.findById(jobId)
    }

    /**
     * Gets logs for a job
     * @param jobId The job ID
     * @return The logs for the job
     */
    fun getJobLogs(jobId: String): List<CommandLog> {
        val logs = repository.findLogsByJobId(jobId)
        logger.info("Retrieved ${logs.size} logs for job $jobId")

        // If no logs were found, log a warning
        if (logs.isEmpty()) {
            logger.warn("No logs found for job $jobId")
        } else {
            // Log the first few logs for debugging
            logs.take(2).forEachIndexed { index, log ->
                logger.debug("Log $index for job $jobId: command=${log.command.command}, exitCode=${log.status}, outputLength=${log.output.length}")
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
        val job = repository.findById(jobId) ?: return null

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
        repository.update(cancelledJob)

        // Log the cancellation
        if (processesCancelled) {
            logger.info("Job $jobId cancelled with running processes terminated")
        } else {
            logger.info("Job $jobId cancelled, no running processes found")
        }

        return cancelledJob
    }

    /**
     * Executes a job by running its commands sequentially
     * @param job The job to execute
     */
    private fun executeJob(job: Job) {
        runCatching {
            // Update job status to RUNNING
            val startedJob = job.copy(
                status = JobStatus.RUNNING,
                startedAt = LocalDateTime.now()
            )
            repository.update(startedJob)

            // Execute commands and collect results
            val commandResults = executeCommands(startedJob)

            // Determine overall job status based on command results
            val jobStatus = determineJobStatus(commandResults)

            // Update job status based on command execution results
            val finalJob = startedJob.copy(
                status = jobStatus,
                completedAt = LocalDateTime.now()
            )
            repository.update(finalJob)

            // Call webhook if provided
            finalJob.webhookUrl?.let { callWebhook(finalJob) }

        }.onFailure { exception ->
            logger.error("Error executing job ${job.id}", exception)

            // Update job status to FAILED
            val failedJob = job.copy(
                status = JobStatus.FAILED,
                startedAt = job.startedAt ?: LocalDateTime.now(),
                completedAt = LocalDateTime.now()
            )
            repository.update(failedJob)
        }
    }

    /**
     * Determine the overall job status based on command execution results
     */
    private fun determineJobStatus(commandResults: List<CommandResult>): JobStatus {
        // If all commands are successful or harmless (skipped), job is completed
        // Otherwise, job is considered failed but all commands were still executed
        if(commandResults.all { it.isSuccess }) {
            return JobStatus.COMPLETED
        }

        if(commandResults.none { it.isSuccess }) {
            return JobStatus.FAILED
        }

        return JobStatus.PARTIALLY_COMPLETED
    }

    /**
     * Data class to track command execution results
     */
    private data class CommandResult(
        val isSuccess: Boolean,
        val status: CommandStatus
    )

    /**
     * Executes all commands in a job sequentially, with each command executed independently
     * @param job The job containing commands to execute
     * @return List of command results
     */
    private fun executeCommands(job: Job): List<CommandResult> {
        return job.commands.mapIndexed { index, command ->
            executeCommand(job.id, index, command).fold(
                onSuccess = { log ->
                    // Log result based on command status
                    when (log.status) {
                        CommandStatus.SUCCESS -> {
                            logger.info("Command executed successfully: ${command.command}")
                        }

                        CommandStatus.HARMFUL -> {
                            logger.warn("Harmful command skipped: ${command.command}")
                        }

                        else -> {
                            logger.error("Command failed: ${command.command}, status: ${log.status}")
                        }
                    }

                    CommandResult(isSuccess = true, status = log.status)
                },
                onFailure = { exception ->
                    logger.error("Exception executing command: ${command.command}", exception)

                    // Create and save an error log for the failed command
                    val errorLog = CommandLog(
                        jobId = job.id,
                        commandIndex = index,
                        command = command,
                        output = "Error executing command: ${exception.message}",
                        status = CommandStatus.ERROR,
                        startTime = LocalDateTime.now(),
                        endTime = LocalDateTime.now()
                    )
                    repository.saveLog(errorLog)

                    CommandResult(isSuccess = false, status = errorLog.status)
                }
            )
        }
    }

    /**
     * Executes a single command step and returns the result
     * @param jobId The job ID
     * @param index The command index in the job
     * @param command The command to execute
     * @return Result containing the command log or an exception
     */
    private fun executeCommand(jobId: String, index: Int, command: Command): Result<CommandLog> = runCatching {
        // Create streaming output handler
        val startTime = LocalDateTime.now()
        val currentOutput = StringBuilder()

        val outputCallback = { line: String ->
            currentOutput.append(line).append("\n")

            // Create and save partial log with current output
            val partialLog = CommandLog(
                jobId = jobId,
                commandIndex = index,
                command = command,
                output = currentOutput.toString(),
                status = CommandStatus.RUNNING,
                startTime = startTime,
                endTime = LocalDateTime.now()
            )
            repository.saveLog(partialLog)
        }

        // Execute command with callback
        val log = commandExecutorService.executeCommand(jobId, index, command, outputCallback)

        // Save final log
        logger.info("Saving final log for job $jobId, command index $index: ${command.command}")
        repository.saveLog(log)
        logger.debug("Log saved for job $jobId, command index $index, output length: ${log.output.length}")

        log
    }

    /**
     * Calls a webhook with the job status
     * @param job The job
     */
    private fun callWebhook(job: Job) {
        val webhookUrl = job.webhookUrl ?: run {
            logger.warn("No webhook URL provided for job ${job.id}")
            return
        }

        runCatching {
            logger.info("Calling webhook($webhookUrl) for job ${job.id}")
            val webhookResponse = JobResponse(
                jobId = job.id,
                status = job.status,
                createdAt = job.createdAt,
                startedAt = job.startedAt,
                completedAt = job.completedAt
            )

            val result = webClient.post()
                .uri(webhookUrl)
                .bodyValue(objectMapper.writeValueAsString(webhookResponse))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
            logger.info("Webhook call result: $result")
        }.onFailure { exception ->
            logger.error("Error calling webhook for job ${job.id}", exception)
        }
    }
}