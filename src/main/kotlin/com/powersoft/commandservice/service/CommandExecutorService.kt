package com.powersoft.commandservice.service

import com.powersoft.commandservice.model.Command
import com.powersoft.commandservice.model.CommandLog
import com.powersoft.commandservice.util.CommandValidator
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Service for executing system commands
 */
@Service
class CommandExecutorService(private val commandValidator: CommandValidator) {

    /**
     * Executes a command and returns the result
     * @param command The command to execute
     * @return A CommandLog containing the execution result
     */
    fun executeCommand(jobId: String, commandIndex: Int, command: Command): CommandLog {
        val startTime = LocalDateTime.now()
        
        // Validate and sanitize the command
        if (!commandValidator.isCommandSafe(command)) {
            return CommandLog(
                jobId = jobId,
                commandIndex = commandIndex,
                command = command,
                output = "Command rejected: Potentially harmful command",
                exitCode = -1,
                startTime = startTime,
                endTime = LocalDateTime.now()
            )
        }
        
        val sanitizedCommand = commandValidator.sanitizeCommand(command)
        
        // Prepare the process builder
        val processBuilder = ProcessBuilder()
        
        // Set the command and arguments
        val commandList = mutableListOf(sanitizedCommand.command)
        commandList.addAll(sanitizedCommand.arguments)
        processBuilder.command(commandList)
        
        // Set working directory if specified
        sanitizedCommand.workingDirectory?.let {
            processBuilder.directory(File(it))
        }
        
        // Redirect error stream to output stream
        processBuilder.redirectErrorStream(true)
        
        try {
            // Start the process
            val process = processBuilder.start()
            
            // Read the output
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            // Wait for the process to complete with a timeout
            val completed = process.waitFor(60, TimeUnit.SECONDS)
            
            // Get the exit code
            val exitCode = if (completed) process.exitValue() else -1
            
            // If the process didn't complete, destroy it
            if (!completed) {
                process.destroy()
            }
            
            return CommandLog(
                jobId = jobId,
                commandIndex = commandIndex,
                command = sanitizedCommand,
                output = output.toString(),
                exitCode = exitCode,
                startTime = startTime,
                endTime = LocalDateTime.now()
            )
        } catch (e: Exception) {
            return CommandLog(
                jobId = jobId,
                commandIndex = commandIndex,
                command = sanitizedCommand,
                output = "Error executing command: ${e.message}",
                exitCode = -1,
                startTime = startTime,
                endTime = LocalDateTime.now()
            )
        }
    }
}