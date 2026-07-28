package com.kairo.timemanager.data.repo

import com.kairo.timemanager.data.local.dao.RecommendationDao
import com.kairo.timemanager.data.local.entity.RecommendationEntity
import com.kairo.timemanager.data.local.entity.RecommendationStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationRepository @Inject constructor(
    private val dao: RecommendationDao
) {

    fun observeOpen(): Flow<List<RecommendationEntity>> = dao.observeOpen()

    fun observeRecent(limit: Int = 30): Flow<List<RecommendationEntity>> = dao.observeRecent(limit)

    suspend fun add(recommendation: RecommendationEntity): Long = dao.insert(recommendation)

    suspend fun accept(id: Long) = dao.setStatus(id, RecommendationStatus.ACCEPTED)

    suspend fun dismiss(id: Long) = dao.setStatus(id, RecommendationStatus.DISMISSED)

    suspend fun countSince(since: Long): Int = dao.countSince(since)

    /** Keeps the table from growing forever; a month of history is plenty for the Coach tab. */
    suspend fun prune(olderThanMillis: Long = 30L * 24 * 60 * 60 * 1000) {
        dao.deleteOlderThan(System.currentTimeMillis() - olderThanMillis)
    }
}
