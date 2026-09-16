package de.pilzscout.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.pilzscout.app.ml.NoOpViewTypeDetector
import de.pilzscout.app.ml.ViewTypeDetector
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {
    @Binds
    @Singleton
    abstract fun bindViewTypeDetector(impl: NoOpViewTypeDetector): ViewTypeDetector
}
