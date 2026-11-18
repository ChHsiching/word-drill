package com.github.chsiching.worddrill.di

import com.github.chsiching.worddrill.update.ApkInstaller
import com.github.chsiching.worddrill.update.DownloadManagerApkInstaller
import com.github.chsiching.worddrill.update.GitHubUpdateChecker
import com.github.chsiching.worddrill.update.UpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideUpdateChecker(): UpdateChecker = GitHubUpdateChecker()

    @Provides
    @Singleton
    fun provideApkInstaller(impl: DownloadManagerApkInstaller): ApkInstaller = impl
}
