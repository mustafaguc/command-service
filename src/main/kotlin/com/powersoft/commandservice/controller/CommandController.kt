package com.powersoft.commandservice.controller

import com.powersoft.commandservice.model.CommandRequest
import com.powersoft.commandservice.model.JobLogsResponse
import com.powersoft.commandservice.model.JobResponse
import com.powersoft.commandservice.service.JobService
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for command execution
 */
@RestController
@RequestMapping("/api/commands")
class CommandController(private val jobService: JobService) {

    /**
     * Submits commands for execution
     * @param commandRequest The command request
     * @return A response containing the job ID
     */
    @PostMapping
    fun submitCommands(@RequestBody commandRequest: CommandRequest): ResponseEntity<JobResponse> {
        val job = jobService.createJob(commandRequest)

        val response = JobResponse(
            jobId = job.id,
            status = job.status,
            createdAt = job.createdAt,
            startedAt = job.startedAt,
            completedAt = job.completedAt
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * Gets the status of a job
     * @param jobId The job ID
     * @return The job status
     */
    @GetMapping("/{jobId}")
    fun getJobStatus(
        @Parameter(description = "ID of the job to retrieve", required = true)
        @PathVariable jobId: String
    ): ResponseEntity<JobResponse> {
        val job = jobService.getJob(jobId) ?: return ResponseEntity.notFound().build()

        val response = JobResponse(
            jobId = job.id,
            status = job.status,
            createdAt = job.createdAt,
            startedAt = job.startedAt,
            completedAt = job.completedAt
        )

        return ResponseEntity.ok(response)
    }

    /**
     * Gets the logs for a job
     * @param jobId The job ID
     * @return The job logs
     */
    @GetMapping("/{jobId}/logs")
    fun getJobLogs(
        @Parameter(description = "ID of the job to retrieve logs for", required = true)
        @PathVariable jobId: String
    ): ResponseEntity<JobLogsResponse> {
        val job = jobService.getJob(jobId) ?: return ResponseEntity.notFound().build()
        val logs = jobService.getJobLogs(jobId)

        val response = JobLogsResponse(
            jobId = job.id,
            status = job.status,
            logs = logs
        )

        return ResponseEntity.ok(response)
    }
}