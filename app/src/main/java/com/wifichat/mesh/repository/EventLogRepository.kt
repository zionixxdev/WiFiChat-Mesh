package com.wifichat.mesh.repository

import com.wifichat.mesh.data.dao.EventLogDao
import com.wifichat.mesh.data.entity.EventLogEntity
import com.wifichat.mesh.data.toModel
import com.wifichat.mesh.model.EventType
import com.wifichat.mesh.model.MeshEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The mesh event log shown on the Mesh screen. Entries never contain message
 * plaintext — only structural facts (ids, counts, states).
 */
class EventLogRepository(private val eventLogDao: EventLogDao) {

    fun observeRecent(limit: Int = 200): Flow<List<MeshEvent>> =
        eventLogDao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    suspend fun log(type: EventType, detail: String) {
        eventLogDao.insert(
            EventLogEntity(
                type = type.name,
                timestamp = System.currentTimeMillis(),
                detail = detail
            )
        )
    }

    suspend fun clear() = eventLogDao.clear()
}
