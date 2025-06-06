package com.powersoft.commandservice.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * Represents a command to be executed
 */
@Schema(description = "A command to be executed")
data class Command(
    val command: String = "ls",
    val arguments: List<String> = listOf("-a","/tmp"),
    val workingDirectory: String? = null
)

/**
 * Represents a job that contains multiple commands to be executed sequentially
 */
data class Job(
    val id: String,
    val commands: List<Command>,
    val webhookUrl: String? = null,
    val status: JobStatus = JobStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val startedAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null
)

/**
 * Represents the status of a job
 */
@Schema(description = "Status of a command execution job")
enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * Represents a log entry for a command execution
 */
@Schema(description = "Log entry for a command execution")
data class CommandLog(
    val jobId: String,
    val commandIndex: Int,
    val command: Command,
    val output: String,
    val exitCode: Int,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
)

/**
 * Request object for submitting commands
 */
data class CommandRequest(
    val commands: List<Command>,
    val webhookUrl: String? = null
)

/**
 * Response object for a job
 */
data class JobResponse(
    val jobId: String,
    val status: JobStatus,
    val createdAt: LocalDateTime,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?
)

/**
 * Response object for job logs
 */
@Schema(description = "Response containing job logs")
data class JobLogsResponse(
    val jobId: String,
    val status: JobStatus,
    val logs: List<CommandLog>
)