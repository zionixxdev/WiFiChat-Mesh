package com.wifichat.mesh.repository

import com.wifichat.mesh.data.dao.PeerDao
import com.wifichat.mesh.data.entity.PeerEntity
import com.wifichat.mesh.data.toModel
import com.wifichat.mesh.model.Peer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PeerRepository(private val peerDao: PeerDao) {

    fun observeAll(): Flow<List<Peer>> =
        peerDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun upsert(peer: Peer) {
        peerDao.upsert(
            PeerEntity(
                peerId = peer.peerId,
                displayName = peer.displayName,
                publicKey = peer.publicKey,
                lastSeen = peer.lastSeen,
                hopCount = peer.hopCount
            )
        )
    }

    suspend fun byId(peerId: String): Peer? = peerDao.byId(peerId)?.toModel()

    suspend fun delete(peerId: String) = peerDao.delete(peerId)

    suspend fun deleteStale(cutoff: Long) = peerDao.deleteStale(cutoff)

    suspend fun count(): Int = peerDao.count()

    suspend fun deleteAll() = peerDao.deleteAll()
}
