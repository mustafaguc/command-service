package com.powersoft.commandservice.service

import com.powersoft.commandservice.model.Command
import com.powersoft.commandservice.model.CommandLog
import com.powersoft.commandservice.model.CommandStatus
import com.powersoft.commandservice.util.CommandValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for executing system commands
 */
@Service
class CommandExecutorService(private val commandValidator: CommandValidator) {
    private val logger = LoggerFactory.getLogger(CommandExecutorService::class.java)

    // Map to track running processes by job ID
    private val runningProcesses = ConcurrentHashMap<String, MutableList<Process>>()

    /**
     * Executes a command and returns the result
     * @param command The command to execute
     * @return A CommandLog containing the execution result
     */
    fun executeCommand(
        jobId: String,
        commandIndex: Int,
        command: Command,
        outputCallback: ((String) -> Unit) = {}
    ): CommandLog {
        val startTime = LocalDateTime.now()

        logger.info("Executing command: ${command.command} ${command.arguments.joinToString(" ")}")

        return when {
            commandValidator.isCommandSafe(command) -> executeValidCommand(
                jobId,
                commandIndex,
                command,
                startTime,
                outputCallback
            )

            else -> createRejectedCommandLog(jobId, commandIndex, command, startTime)
        }
    }

    private fun createRejectedCommandLog(
        jobId: String,
        commandIndex: Int,
        command: Command,
        startTime: LocalDateTime
    ): CommandLog {
        logger.warn("Command rejected as potentially harmful: ${command.command} ${command.arguments.joinToString(" ")}")

        return CommandLog(
            jobId = jobId,
            commandIndex = commandIndex,
            command = command,
            output = "Command rejected: Potentially harmful command",
            exitCode = -2, // Special exit code for harmful commands
            status = CommandStatus.HARMFUL,
            startTime = startTime,
            endTime = LocalDateTime.now()
        )
    }

    private fun executeValidCommand(
        jobId: String,
        commandIndex: Int,
        command: Command,
        startTime: LocalDateTime,
        outputCallback: ((String) -> Unit) = {}
    ): CommandLog {
        val sanitizedCommand = commandValidator.sanitizeCommand(command)

        return try {
            // Create and configure process builder
            val processBuilder = createProcessBuilder(sanitizedCommand)

            // Execute the process and capture output
            val process = processBuilder.start()

            // Add process to the running processes map
            runningProcesses.computeIfAbsent(jobId) { mutableListOf() }.add(process)

            val output = captureProcessOutput(process, outputCallback)
            val exitCode = process.waitFor()

            // Clean up if needed
            if (exitCode == -1 || !process.isAlive) {
                process.destroy()
            }

            // Remove process from the running processes map
            runningProcesses[jobId]?.remove(process)
            if (runningProcesses[jobId]?.isEmpty() == true) {
                runningProcesses.remove(jobId)
            }

            createSuccessCommandLog(jobId, commandIndex, sanitizedCommand, output, exitCode, startTime)
        } catch (e: Exception) {
            logger.error("Error executing command: ${e.message}", e)
            createErrorCommandLog(jobId, commandIndex, sanitizedCommand, e, startTime)
        }
    }

    private fun createProcessBuilder(command: Command): ProcessBuilder {
        val fullCommand = listOf(command.command) + command.arguments

        return ProcessBuilder(fullCommand).apply {
            command.workingDirectory?.let { directory(File(it)) }
            redirectErrorStream(true)
        }
    }

    private fun captureProcessOutput(process: Process, lineCallback: (String) -> Unit = {}): String {
        val outputBuilder = StringBuilder()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Print to console for real-time visibility
                println(line)

                // Call the callback with each line
                line?.let(lineCallback)

                // Append to our output string
                outputBuilder.append(line).append("\n")
            }
        }

        return outputBuilder.toString()
    }

    private fun createSuccessCommandLog(
        jobId: String,
        commandIndex: Int,
        command: Command,
        output: String,
        exitCode: Int,
        startTime: LocalDateTime
    ): CommandLog {
        val endTime = LocalDateTime.now()
        val executionTime = java.time.Duration.between(startTime, endTime).toMillis()

        logger.info("Command completed: ${command.command} ${command.arguments.joinToString(" ")}")
        logger.info("Exit code: $exitCode, Execution time: ${executionTime}ms")

        // Log output summary (first few lines if output is long)
        val outputLines = output.lines()
        val outputSummary = if (outputLines.size > 5) {
            outputLines.take(5).joinToString("\n") + "\n... (${outputLines.size - 5} more lines)"
        } else {
            output
        }
        logger.debug("Command output: \n$outputSummary")

        return CommandLog(
            jobId = jobId,
            commandIndex = commandIndex,
            command = command,
            output = output,
            exitCode = exitCode,
            status = CommandStatus.SUCCESS,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun createErrorCommandLog(
        jobId: String,
        commandIndex: Int,
        command: Command,
        exception: Exception,
        startTime: LocalDateTime
    ): CommandLog {
        val endTime = LocalDateTime.now()
        val executionTime = java.time.Duration.between(startTime, endTime).toMillis()

        logger.error("Command failed: ${command.command} ${command.arguments.joinToString(" ")}")
        logger.error("Execution time: ${executionTime}ms, Error: ${exception.message}")

        return CommandLog(
            jobId = jobId,
            commandIndex = commandIndex,
            command = command,
            output = "Error executing command: ${exception.message}",
            exitCode = -1,
            status = CommandStatus.FAILED,
            startTime = startTime,
            endTime = endTime
        )
    }

    /**
     * Cancels all running processes for a specific job ID
     * @param jobId The job ID to cancel processes for
     * @return true if any processes were cancelled, false otherwise
     */
    fun cancelJob(jobId: String): Boolean {
        logger.info("Cancelling all processes for job $jobId")

        val processes = runningProcesses[jobId]
        if (processes.isNullOrEmpty()) {
            logger.info("No running processes found for job $jobId")
            return false
        }

        var cancelled = false
        processes.forEach { process ->
            try {
                if (process.isAlive) {
                    // Force destroy the process
                    process.destroyForcibly()
                    cancelled = true
                    logger.info("Process for job $jobId forcibly terminated")
                }
            } catch (e: Exception) {
                logger.error("Error cancelling process for job $jobId: ${e.message}", e)
            }
        }

        // Clear the processes list for this job
        runningProcesses.remove(jobId)

        return cancelled
    }
}