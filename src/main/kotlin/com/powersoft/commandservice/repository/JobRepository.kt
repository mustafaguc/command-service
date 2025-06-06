package com.powersoft.commandservice.repository

import com.powersoft.commandservice.model.CommandStatus
import com.powersoft.commandservice.model.CommandLog
import com.powersoft.commandservice.model.Job
import com.powersoft.commandservice.model.isRunning
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

/**
 * Repository interface for Job operations
 */
interface JobRepository {
    fun save(job: Job): Job
    fun findById(id: String): Job?
    fun update(job: Job): Job
    fun saveLog(log: CommandLog)
    fun findLogsByJobId(jobId: String): List<CommandLog>
}

/**
 * In-memory implementation of JobRepository
 */
@Repository
class InMemoryJobRepository : JobRepository {
    private val logger = LoggerFactory.getLogger(InMemoryJobRepository::class.java)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val logs = ConcurrentHashMap<String, MutableList<CommandLog>>()

    override fun save(job: Job): Job {
        jobs[job.id] = job
        return job
    }

    override fun findById(id: String): Job? {
        return jobs[id]
    }

    override fun update(job: Job): Job {
        jobs[job.id] = job
        return job
    }

    override fun saveLog(log: CommandLog) {
        // Get or create the log list for this job
        val jobLogs = logs.computeIfAbsent(log.jobId) { mutableListOf() }
        
        // Check if this is a partial log update (exitCode == -999 indicates a running command)
        if (log.isRunning()) {
            // Find and replace any existing partial log for this command index
            val existingLogIndex = jobLogs.indexOfFirst {
                it.commandIndex == log.commandIndex && it.isRunning()
            }
            
            if (existingLogIndex >= 0) {
                jobLogs[existingLogIndex] = log
            } else {
                jobLogs.add(log)
            }
        } else {
            // This is a final log, replace any partial log for this command index
            val existingLogIndex = jobLogs.indexOfFirst { it.commandIndex == log.commandIndex }
            
            if (existingLogIndex >= 0) {
                jobLogs[existingLogIndex] = log
            } else {
                jobLogs.add(log)
            }
        }
    }

    override fun findLogsByJobId(jobId: String) = logs[jobId]?.toList() ?: emptyList()
}