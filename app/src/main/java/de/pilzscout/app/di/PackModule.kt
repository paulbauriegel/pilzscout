package de.pilzscout.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.pilzscout.app.BuildConfig
import de.pilzscout.app.pack.AssetPackSource
import de.pilzscout.app.pack.PackSource
import de.pilzscout.app.pack.RemotePackSource
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PackModule {
    /** `bundled` flavour: the pack in the APK assets. `play` flavour: the pack repository (Hugging Face). */
    @Provides
    @Singleton
    fun providePackSource(assets: Provider<AssetPackSource>, remote: Provider<RemotePackSource>): PackSource =
        if (BuildConfig.PACK_REMOTE) remote.get() else assets.get()
}
