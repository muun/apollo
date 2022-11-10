package io.muun.apollo.presentation.ui.activity.extension

import android.view.WindowManager
import io.muun.apollo.BuildConfig
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.logging.Crashlytics.logBreadcrumb
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.di.PerActivity
import javax.inject.Inject

@PerActivity
class ScreenshotBlockExtension @Inject constructor() : ActivityExtension() {

    fun startBlockingScreenshots(caller: String) {
        if (BuildConfig.PRODUCTION && Globals.isReleaseBuild()) {
            logBreadcrumb("blockscreenshots: $caller ${activity.javaClass.simpleName} START")
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // prevent screenshots
        }
    }

    fun stopBlockingScreenshots(caller: String) {
        if (BuildConfig.PRODUCTION && Globals.isReleaseBuild()) {
            logBreadcrumb("blockscreenshots: $caller ${activity.javaClass.simpleName} STOP")
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}