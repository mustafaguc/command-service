package com.powersoft.commandservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.powersoft.commandservice.model.Command
import com.powersoft.commandservice.model.CommandRequest
import com.powersoft.commandservice.model.Job
import com.powersoft.commandservice.model.JobStatus
import com.powersoft.commandservice.service.JobService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(CommandController::class)
class CommandControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var jobService: JobService

    @Test
    fun `should submit commands and return job ID`() {
        // Arrange
        val commandRequest = CommandRequest(
            commands = listOf(
                Command("echo", listOf("Hello, World!")),
                Command("ls", listOf("-la"))
            ),
            webhookUrl = "http://example.com/webhook"
        )

        val jobId = UUID.randomUUID().toString()
        val createdAt = LocalDateTime.now()
        
        val job = Job(
            id = jobId,
            commands = commandRequest.commands,
            webhookUrl = commandRequest.webhookUrl,
            status = JobStatus.PENDING,
            createdAt = createdAt
        )

        `when`(jobService.createJob(commandRequest)).thenReturn(job)

        // Act & Assert
        mockMvc.perform(
            post("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(commandRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.jobId").value(jobId))
            .andExpect(jsonPath("$.status").value(JobStatus.PENDING.toString()))
    }

    @Test
    fun `should get job status`() {
        // Arrange
        val jobId = UUID.randomUUID().toString()
        val createdAt = LocalDateTime.now()
        val startedAt = createdAt.plusSeconds(1)
        
        val job = Job(
            id = jobId,
            commands = listOf(Command("echo", listOf("Hello, World!"))),
            status = JobStatus.RUNNING,
            createdAt = createdAt,
            startedAt = startedAt
        )

        `when`(jobService.getJob(jobId)).thenReturn(job)

        // Act & Assert
        mockMvc.perform(get("/api/commands/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobId").value(jobId))
            .andExpect(jsonPath("$.status").value(JobStatus.RUNNING.toString()))
    }

    @Test
    fun `should return 404 for non-existent job`() {
        // Arrange
        val jobId = UUID.randomUUID().toString()

        `when`(jobService.getJob(jobId)).thenReturn(null)

        // Act & Assert
        mockMvc.perform(get("/api/commands/$jobId"))
            .andExpect(status().isNotFound)
    }
}