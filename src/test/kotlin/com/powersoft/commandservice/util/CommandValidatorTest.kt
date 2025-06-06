package com.powersoft.commandservice.util

import com.powersoft.commandservice.model.Command
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CommandValidatorTest {

    @Autowired
    private lateinit var commandValidator: CommandValidator

    @Test
    fun `should reject dangerous commands`() {
        // Arrange
        val dangerousCommands = listOf(
            Command("kill -9 1234"),
            Command("rm -rf /"),
            Command("shutdown now"),
            Command("reboot"),
            Command("echo hello && rm -rf /"),
            Command("echo", listOf("hello", "; rm -rf /")),
            Command("echo `rm -rf /`"),
            Command("echo $(rm -rf /)")
        )

        // Act & Assert
        dangerousCommands.forEach { command ->
            assertFalse(commandValidator.isCommandSafe(command), "Command should be rejected: $command")
        }
    }

    @Test
    fun `should accept safe commands`() {
        // Arrange
        val safeCommands = listOf(
            Command("echo", listOf("hello world")),
            Command("ls", listOf("-la")),
            Command("pwd"),
            Command("cat", listOf("file.txt")),
            Command("mkdir", listOf("test-dir")),
            Command("date")
        )

        // Act & Assert
        safeCommands.forEach { command ->
            assertTrue(commandValidator.isCommandSafe(command), "Command should be accepted: $command")
        }
    }

    @Test
    fun `should sanitize commands properly`() {
        // Arrange
        val command = Command("echo", listOf("Hello & World", "Test > file.txt"))

        // Act
        val sanitizedCommand = commandValidator.sanitizeCommand(command)

        // Assert
        assertEquals("echo", sanitizedCommand.command)
        assertEquals("Hello \\& World", sanitizedCommand.arguments[0])
        assertEquals("Test \\> file.txt", sanitizedCommand.arguments[1])
    }
}