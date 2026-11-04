package io.muun.apollo.presentation.app.di.modules

import dagger.Binds
import dagger.Module
import io.muun.apollo.presentation.ui.base.RxSubscriptions
import io.muun.apollo.presentation.ui.base.RxSubscriptionsMixin

@Module
interface RxSubscriptionsModule {

    // Unscoped on purpose: every ViewModel must get its own mixin instance.
    @Binds
    fun bindRxSubscriptions(impl: RxSubscriptionsMixin): RxSubscriptions
}
