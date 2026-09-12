package de.pilzscout.app.data.species

import androidx.room.Dao
import androidx.room.Query

data class SpeciesSummary(
    val id: String,
    @androidx.room.ColumnInfo(name = "scientific_name") val scientificName: String,
    val binomial: String,
    val genus: String,
    val family: String?,
    @androidx.room.ColumnInfo(name = "common_de") val commonDe: String?,
    @androidx.room.ColumnInfo(name = "common_en") val commonEn: String?,
    val poisonous: Int,
    @androidx.room.ColumnInfo(name = "in_germany") val inGermany: Int,
    @androidx.room.ColumnInfo(name = "model_class_index") val modelClassIndex: Int,
    @androidx.room.ColumnInfo(name = "n_observations") val nObservations: Int,
    val edibility: String?,
)

data class GroupCount(val name: String, val count: Int)

@Dao
interface SpeciesDao {
    @Query("SELECT * FROM species WHERE id = :id")
    suspend fun byId(id: String): SpeciesEntity?

    @Query("SELECT * FROM species WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<SpeciesEntity>

    @Query("SELECT * FROM species WHERE model_class_index = :index")
    suspend fun byClassIndex(index: Int): SpeciesEntity?

    @Query("SELECT model_class_index, id, in_germany FROM species ORDER BY model_class_index")
    suspend fun classIndexTable(): List<ClassIndexRow>

    @Query(
        """SELECT id, scientific_name, binomial, genus, family, common_de, common_en, poisonous, in_germany,
                  model_class_index, n_observations, edibility FROM species
           ORDER BY in_germany DESC, n_observations DESC, binomial LIMIT :limit OFFSET :offset""",
    )
    suspend fun page(limit: Int, offset: Int): List<SpeciesSummary>

    @Query(
        """SELECT DISTINCT s.id, s.scientific_name, s.binomial, s.genus, s.family, s.common_de, s.common_en,
                  s.poisonous, s.in_germany, s.model_class_index, s.n_observations, s.edibility
           FROM species s JOIN species_search ss ON ss.species_id = s.id
           WHERE ss.text_norm LIKE :pattern
           ORDER BY s.in_germany DESC, s.n_observations DESC, s.binomial LIMIT :limit""",
    )
    suspend fun search(pattern: String, limit: Int): List<SpeciesSummary>

    @Query(
        """SELECT id, scientific_name, binomial, genus, family, common_de, common_en, poisonous, in_germany,
                  model_class_index, n_observations, edibility FROM species WHERE genus = :genus ORDER BY binomial""",
    )
    suspend fun byGenus(genus: String): List<SpeciesSummary>

    @Query("SELECT family AS name, COUNT(*) AS count FROM species WHERE family IS NOT NULL GROUP BY family ORDER BY family")
    suspend fun families(): List<GroupCount>

    @Query("SELECT genus AS name, COUNT(*) AS count FROM species WHERE family = :family GROUP BY genus ORDER BY genus")
    suspend fun generaInFamily(family: String): List<GroupCount>

    @Query("SELECT COUNT(*) FROM species")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM species WHERE in_germany = 1")
    suspend fun countInGermany(): Int
}

data class ClassIndexRow(
    @androidx.room.ColumnInfo(name = "model_class_index") val modelClassIndex: Int,
    val id: String,
    @androidx.room.ColumnInfo(name = "in_germany") val inGermany: Int,
)

@Dao
interface TraitDao {
    @Query("SELECT * FROM trait WHERE species_id = :speciesId AND lang = :lang ORDER BY feature, basis")
    suspend fun forSpecies(speciesId: String, lang: String): List<TraitEntity>

    @Query("SELECT * FROM species_stats WHERE species_id = :speciesId")
    suspend fun stats(speciesId: String): SpeciesStatsEntity?

    @Query("SELECT * FROM species_stats WHERE species_id IN (:ids)")
    suspend fun statsFor(ids: List<String>): List<SpeciesStatsEntity>

    @Query("SELECT species_id, month_hist_json FROM species_stats")
    suspend fun allMonthHistograms(): List<MonthHistRow>
}

data class MonthHistRow(
    @androidx.room.ColumnInfo(name = "species_id") val speciesId: String,
    @androidx.room.ColumnInfo(name = "month_hist_json") val monthHistJson: String,
)

@Dao
interface WikiDao {
    @Query("SELECT * FROM wiki_article WHERE species_id = :speciesId AND lang = :lang")
    suspend fun article(speciesId: String, lang: String): WikiArticleEntity?

    @Query("SELECT lang FROM wiki_article WHERE species_id = :speciesId")
    suspend fun availableLanguages(speciesId: String): List<String>
}

@Dao
interface FungiTasticDao {
    @Query("SELECT * FROM fungitastic_observation WHERE species_id = :speciesId ORDER BY dna_sequenced DESC, event_date DESC")
    suspend fun observationsForSpecies(speciesId: String): List<FtObservationEntity>

    @Query("SELECT * FROM fungitastic_observation WHERE observation_id = :id")
    suspend fun observation(id: Long): FtObservationEntity?

    @Query("SELECT * FROM fungitastic_photo WHERE observation_id = :observationId ORDER BY filename")
    suspend fun photosForObservation(observationId: Long): List<FtPhotoEntity>

    @Query("SELECT * FROM fungitastic_photo WHERE species_id = :speciesId ORDER BY observation_id, filename LIMIT :limit")
    suspend fun photosForSpecies(speciesId: String, limit: Int): List<FtPhotoEntity>

    @Query("SELECT * FROM fungitastic_photo WHERE species_id = :speciesId ORDER BY observation_id, filename LIMIT 1")
    suspend fun leadPhoto(speciesId: String): FtPhotoEntity?

    @Query("SELECT * FROM fungitastic_observation ORDER BY event_date DESC LIMIT :limit OFFSET :offset")
    suspend fun recentObservations(limit: Int, offset: Int): List<FtObservationEntity>
}
