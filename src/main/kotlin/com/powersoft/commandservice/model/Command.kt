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
 * Exit codes for command execution
 */
enum class CommandStatus(val code: Int) {
    SUCCESS(0),           // Command executed successfully
    ERROR(-1),            // General error during execution
    HARMFUL(-2),          // Command was rejected as potentially harmful
    RUNNING(-999);        // Command is still running

    companion object {
        fun fromCode(code: Int): CommandStatus {
            return CommandStatus.entries.find { it.code == code } ?: when {
                code == 0 -> SUCCESS
                code == -2 -> HARMFUL
                code == -999 -> RUNNING
                else -> ERROR
            }
        }
    }
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
    val status: CommandStatus,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
)

fun CommandLog.isRunning() = status == CommandStatus.RUNNING
fun CommandLog.isFailed() = status == CommandStatus.ERROR
fun CommandLog.isSuccessful() = status == CommandStatus.SUCCESS
fun CommandLog.isHarmful() = status == CommandStatus.HARMFUL


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
                successfulCommands = logs.count(CommandLog::isSuccessful),
                failedCommands = logs.count(CommandLog::isFailed),
                skippedHarmfulCommands = logs.count(CommandLog::isHarmful)
            )
        }
    }
}