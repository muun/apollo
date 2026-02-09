package io.muun.apollo.presentation.app.di.modules

import dagger.Binds
import dagger.Module
import io.muun.apollo.presentation.biometrics.BiometricsController
import io.muun.apollo.presentation.biometrics.BiometricsControllerImpl

@Module
interface BiometricsModule {

    @Binds
    fun bindBiometricsController(impl: BiometricsControllerImpl): BiometricsController
}
