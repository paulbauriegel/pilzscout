package de.pilzscout.app.data.species

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SpeciesEntity::class,
        SpeciesSearchEntity::class,
        TraitEntity::class,
        SpeciesStatsEntity::class,
        WikiArticleEntity::class,
        FtObservationEntity::class,
        FtPhotoEntity::class,
    ],
    version = SpeciesDatabase.VERSION,
    exportSchema = true,
)
abstract class SpeciesDatabase : RoomDatabase() {
    abstract fun speciesDao(): SpeciesDao
    abstract fun traitDao(): TraitDao
    abstract fun wikiDao(): WikiDao
    abstract fun fungiTasticDao(): FungiTasticDao

    companion object {
        /** Bump together with the pack schemaVersion whenever entities change. */
        const val VERSION = 1
    }
}
