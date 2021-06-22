package io.muun.apollo.presentation.ui.select_night_mode

import android.os.Bundle
import io.muun.apollo.domain.NightModeManager
import io.muun.apollo.domain.model.NightMode
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import javax.inject.Inject
import javax.validation.constraints.NotNull

@PerActivity
class SelectNightModePresenter @Inject constructor(
    private val nightModeManager: NightModeManager
): BasePresenter<SelectNightModeView>() {

    override fun setUp(@NotNull arguments: Bundle) {
        super.setUp(arguments)

        val observable = nightModeManager
            .watch()
            .doOnNext(view::setNightMode)

        subscribeTo(observable)
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_NIGHT_MODE_PICKER()
    }

    fun reportNightModeChange(nightMode: NightMode) {
        nightModeManager.save(nightMode)
        analytics.report(AnalyticsEvent.E_DID_SELECT_NIGHT_MODE(nightMode))
        view.finishActivity()
    }
}