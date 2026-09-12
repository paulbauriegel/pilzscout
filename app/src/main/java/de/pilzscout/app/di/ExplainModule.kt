package de.pilzscout.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.pilzscout.app.explain.ExplanationProvider
import de.pilzscout.app.explain.StubExplanationProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExplainModule {
    @Binds
    @Singleton
    abstract fun bindExplanationProvider(impl: StubExplanationProvider): ExplanationProvider
}
