package com.powersoft.commandservice.util

import com.powersoft.commandservice.model.Command
import org.springframework.stereotype.Component

/**
 * Utility class for validating and sanitizing commands
 */
@Component
class CommandValidator {
    
    // List of dangerous commands that should be blocked
    private val dangerousCommands = setOf(
        "kill", "shutdown", "reboot", "rm", "format", "dd", "mkfs",
        "halt", "poweroff", "init 0", "init 6"
    )
    
    // List of dangerous characters that should be escaped
    private val dangerousChars = setOf(
        '&', ';', '|', '`', '$', '>', '<', '(', ')', '{', '}', '[', ']', '!', '*', '?', '~'
    )
    
    /**
     * Validates if a command is safe to execute
     * @param command The command to validate
     * @return true if the command is safe, false otherwise
     */
    fun isCommandSafe(command: Command): Boolean {
        val commandName = command.command.trim().lowercase()
        
        // Check if the command is in the dangerous commands list
        if (dangerousCommands.any { commandName.startsWith(it) }) {
            return false
        }
        
        // Check for dangerous shell operators
        if (commandName.contains("&&") || 
            commandName.contains("||") || 
            commandName.contains(";") || 
            commandName.contains("|") ||
            commandName.contains("`") ||
            commandName.contains("$(")) {
            return false
        }
        
        // Check arguments for dangerous patterns
        for (arg in command.arguments) {
            if (arg.contains("&&") || 
                arg.contains("||") || 
                arg.contains(";") || 
                arg.contains("|") ||
                arg.contains("`") ||
                arg.contains("$(")) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Sanitizes a command by escaping dangerous characters
     * @param command The command to sanitize
     * @return A sanitized version of the command
     */
    fun sanitizeCommand(command: Command): Command {
        val sanitizedCommand = sanitizeString(command.command)
        val sanitizedArgs = command.arguments.map { sanitizeString(it) }
        
        return Command(
            command = sanitizedCommand,
            arguments = sanitizedArgs,
            workingDirectory = command.workingDirectory
        )
    }
    
    /**
     * Sanitizes a string by escaping dangerous characters
     * @param input The string to sanitize
     * @return A sanitized version of the string
     */
    private fun sanitizeString(input: String): String {
        var result = input
        
        // Escape dangerous characters
        for (char in dangerousChars) {
            result = result.replace(char.toString(), "\\$char")
        }
        
        return result
    }
}