package com.powersoft.commandservice.repository

import com.powersoft.commandservice.model.CommandLog
import com.powersoft.commandservice.model.Job
import java.util.concurrent.ConcurrentHashMap
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
        logs.computeIfAbsent(log.jobId) { mutableListOf() }.add(log)
    }

    override fun findLogsByJobId(jobId: String): List<CommandLog> {
        return logs[jobId]?.toList() ?: emptyList()
    }
}