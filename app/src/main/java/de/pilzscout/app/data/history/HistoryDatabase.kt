package de.pilzscout.app.data.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "observation", indices = [Index("captured_at"), Index("primary_species_id")])
data class ObservationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    val lat: Double?,
    val lon: Double?,
    @ColumnInfo(name = "location_included") val locationIncluded: Boolean,
    @ColumnInfo(name = "place_name", defaultValue = "NULL") val placeName: String? = null,
    @ColumnInfo(name = "primary_species_id") val primarySpeciesId: String,
    @ColumnInfo(name = "primary_prob") val primaryProb: Float,
    val descriptor: String,
    @ColumnInfo(name = "n_photos") val nPhotos: Int,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    @ColumnInfo(name = "model_precision") val modelPrecision: String,
    @ColumnInfo(name = "total_inference_ms") val totalInferenceMs: Long,
    val mode: String,
    @ColumnInfo(name = "user_confirmed") val userConfirmed: Boolean,
    @ColumnInfo(name = "corrected_species_id") val correctedSpeciesId: String?,
    @ColumnInfo(name = "corrected_at") val correctedAt: Long?,
    @ColumnInfo(name = "lead_photo_id") val leadPhotoId: String,
    @ColumnInfo(name = "fusion_json") val fusionJson: String,
    @ColumnInfo(name = "comparison_json") val comparisonJson: String,
    @ColumnInfo(name = "contextual_json") val contextualJson: String,
)

@Entity(tableName = "photo", indices = [Index("observation_id")])
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "observation_id") val observationId: String,
    @ColumnInfo(name = "view_type") val viewType: String,
    val position: Int,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "thumb_path") val thumbPath: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "top1_species_id") val top1SpeciesId: String?,
    @ColumnInfo(name = "top1_prob") val top1Prob: Float?,
    @ColumnInfo(name = "topk_json") val topkJson: String,
    @ColumnInfo(name = "inference_ms") val inferenceMs: Long,
    val agreement: String?,
)

@Entity(tableName = "candidate", primaryKeys = ["observation_id", "rank"])
data class CandidateEntity(
    @ColumnInfo(name = "observation_id") val observationId: String,
    val rank: Int,
    @ColumnInfo(name = "species_id") val speciesId: String,
    val prob: Float,
)

data class ObservationWithPhotos(
    @androidx.room.Embedded val observation: ObservationEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "observation_id") val photos: List<PhotoEntity>,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "observation_id") val candidates: List<CandidateEntity>,
)

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: ObservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<CandidateEntity>)

    @Transaction
    suspend fun insertAll(observation: ObservationEntity, photos: List<PhotoEntity>, candidates: List<CandidateEntity>) {
        insertObservation(observation)
        insertPhotos(photos)
        insertCandidates(candidates)
    }

    @Update
    suspend fun updateObservation(observation: ObservationEntity)

    @Transaction
    @Query("SELECT * FROM observation ORDER BY captured_at DESC")
    fun observeAll(): Flow<List<ObservationWithPhotos>>

    @Transaction
    @Query("SELECT * FROM observation WHERE id = :id")
    suspend fun byId(id: String): ObservationWithPhotos?

    @Transaction
    @Query("SELECT * FROM observation WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<ObservationWithPhotos>

    @Query("DELETE FROM observation WHERE id IN (:ids)")
    suspend fun deleteObservations(ids: List<String>)

    @Query("DELETE FROM photo WHERE observation_id IN (:ids)")
    suspend fun deletePhotos(ids: List<String>)

    @Query("DELETE FROM candidate WHERE observation_id IN (:ids)")
    suspend fun deleteCandidates(ids: List<String>)

    @Transaction
    suspend fun deleteAll(ids: List<String>) {
        deleteCandidates(ids)
        deletePhotos(ids)
        deleteObservations(ids)
    }

    @Query("SELECT COUNT(*) FROM observation")
    fun observeCount(): Flow<Int>
}

@Database(
    entities = [ObservationEntity::class, PhotoEntity::class, CandidateEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [androidx.room.AutoMigration(from = 1, to = 2)],
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
