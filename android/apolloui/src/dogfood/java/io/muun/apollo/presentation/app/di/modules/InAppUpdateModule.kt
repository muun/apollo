package io.muun.apollo.presentation.app.di.modules

import dagger.Module
import dagger.Provides
import io.muun.apollo.presentation.ui.home.InAppUpdateManager
import io.muun.apollo.presentation.ui.home.PlayInAppUpdateManager

@Module
object InAppUpdateModule {

    @Provides
    fun provideInAppUpdateManagerFactory(): InAppUpdateManager.Factory =
        InAppUpdateManager.Factory(::PlayInAppUpdateManager)
}
