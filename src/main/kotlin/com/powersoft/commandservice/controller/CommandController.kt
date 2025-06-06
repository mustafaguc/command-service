package com.powersoft.commandservice.controller

import com.powersoft.commandservice.model.CommandRequest
import com.powersoft.commandservice.service.JobService
import io.swagger.v3.oas.annotations.Parameter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for command execution
 */
@RestController
@RequestMapping("/api/commands")
class CommandController(private val jobService: JobService) {
    private val logger = LoggerFactory.getLogger(CommandController::class.java)

    /**
     * Submits commands for execution
     * @param commandRequest The command request
     * @return A response containing the job ID
     */
    @PostMapping
    fun submitCommands(@RequestBody commandRequest: CommandRequest) =
        ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(commandRequest))

    /**
     * Gets the status of a job
     * @param jobId The job ID
     * @return The job status
     */
    @GetMapping("/{jobId}")
    fun getJobStatus(
        @Parameter(description = "ID of the job to retrieve", required = true) @PathVariable jobId: String
    ) = jobService.getJob(jobId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /**
     * Gets the logs for a job
     * @param jobId The job ID
     * @return The job logs
     */
    @GetMapping("/{jobId}/logs")
    fun getJobLogs(
        @Parameter(description = "ID of the job to retrieve logs for", required = true) @PathVariable jobId: String
    ) = ResponseEntity.ok(jobService.getJobLogs(jobId))

    /**
     * Cancels a job
     * @param jobId The job ID to cancel
     * @return The canceled job status
     */
    @DeleteMapping("/{jobId}")
    fun cancelJob(
        @Parameter(description = "ID of the job to cancel", required = true)
        @PathVariable jobId: String
    ) = jobService.cancelJob(jobId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}