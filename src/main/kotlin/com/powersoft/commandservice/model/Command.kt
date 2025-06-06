package com.powersoft.commandservice.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * Represents a command to be executed
 */
@Schema(description = "A command to be executed")
data class Command(
    val command: String = "ls",
    val arguments: List<String> = listOf("-a", "/tmp"),
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
    FAILED,
    CANCELLED
}

/**
 * Represents a log entry for a command execution
 */
@Schema(description = "Log entry for a command execution")
/**
 * Status of a command execution
 */
enum class CommandStatus {
    SUCCESS,      // Command executed successfully
    FAILED,       // Command failed during execution
    HARMFUL,      // Command was rejected as potentially harmful
    RUNNING       // Command is still running
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
    val status: CommandStatus? = null,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
) {
    /**
     * Gets the effective status of the command, either from the status field or computed from the exit code
     */
    val effectiveStatus: CommandStatus
        get() = status ?: when (exitCode) {
            0 -> CommandStatus.SUCCESS
            -2 -> CommandStatus.HARMFUL
            -999 -> CommandStatus.RUNNING
            else -> CommandStatus.FAILED
        }
}


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
    val logs: List<CommandLog>,
    val summary: JobSummary
)

/**
 * Summary of a job execution
 */
@Schema(description = "Summary of a job execution")
data class JobSummary(
    val totalCommands: Int,
    val successfulCommands: Int,
    val failedCommands: Int,
    val skippedHarmfulCommands: Int
) {
    companion object {
        fun fromLogs(logs: List<CommandLog>): JobSummary {
            return JobSummary(
                totalCommands = logs.size,
                successfulCommands = logs.count { it.effectiveStatus == CommandStatus.SUCCESS },
                failedCommands = logs.count { it.effectiveStatus == CommandStatus.FAILED },
                skippedHarmfulCommands = logs.count { it.effectiveStatus == CommandStatus.HARMFUL }
            )
        }
    }
}