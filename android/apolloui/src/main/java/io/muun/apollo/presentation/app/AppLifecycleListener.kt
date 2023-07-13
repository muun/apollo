package io.muun.apollo.presentation.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent

/**
 * Apparently this won't correctly handle/track app crashes or ANRs.
 * See: https://stackoverflow.com/a/44461605/901465 (and its comments)
 */
class AppLifecycleListener(val analytics: Analytics) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onMoveToForeground() { // app moved to foreground
        analytics.report(AnalyticsEvent.E_APP_WILL_ENTER_FOREGROUND())
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onMoveToBackground() { // app moved to background
        analytics.report(AnalyticsEvent.E_APP_WILL_GO_TO_BACKGROUND())
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() { // app will terminate
        analytics.report(AnalyticsEvent.E_APP_WILL_TERMINATE())
    }
}