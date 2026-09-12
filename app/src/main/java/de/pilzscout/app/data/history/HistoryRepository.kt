package de.pilzscout.app.data.history

import de.pilzscout.app.identify.PhotoStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val db: HistoryDatabase,
    private val photoStore: PhotoStore,
) {
    fun observeAll(): Flow<List<ObservationWithPhotos>> = db.historyDao().observeAll()
    fun observeCount(): Flow<Int> = db.historyDao().observeCount()

    suspend fun byId(id: String): ObservationWithPhotos? = db.historyDao().byId(id)
    suspend fun byIds(ids: List<String>): List<ObservationWithPhotos> = db.historyDao().byIds(ids)

    suspend fun save(observation: ObservationEntity, photos: List<PhotoEntity>, candidates: List<CandidateEntity>) =
        db.historyDao().insertAll(observation, photos, candidates)

    suspend fun update(observation: ObservationEntity) = db.historyDao().updateObservation(observation)

    suspend fun delete(ids: List<String>) {
        db.historyDao().deleteAll(ids)
        ids.forEach { photoStore.deleteObservation(it) }
    }
}
