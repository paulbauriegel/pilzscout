package de.pilzscout.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.pilzscout.app.pack.AssetPackSource
import de.pilzscout.app.pack.PackSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PackModule {
    @Binds
    @Singleton
    abstract fun bindPackSource(impl: AssetPackSource): PackSource
}
