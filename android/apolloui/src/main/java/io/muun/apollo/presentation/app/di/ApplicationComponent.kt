package io.muun.apollo.presentation.app.di

import dagger.Component
import io.muun.apollo.data.di.DataComponent
import io.muun.apollo.presentation.app.ApolloApplication
import io.muun.apollo.presentation.app.di.modules.BiometricsModule
import io.muun.apollo.presentation.app.di.modules.StartupModule
import io.muun.apollo.presentation.ui.base.di.ActivityComponent
import io.muun.apollo.presentation.ui.base.di.FragmentComponent
import io.muun.apollo.presentation.ui.base.di.ViewComponent

@PerApplication
@Component(dependencies = [DataComponent::class], modules = [StartupModule::class, BiometricsModule::class])
interface ApplicationComponent {

    fun inject(application: ApolloApplication)

    fun fragmentComponent(): FragmentComponent

    fun activityComponent(): ActivityComponent

    fun viewComponent(): ViewComponent
}
