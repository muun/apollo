package io.muun.apollo.presentation.ui.activity.extension

import android.view.WindowManager
import io.muun.apollo.BuildConfig
import io.muun.apollo.data.external.Globals
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.di.PerActivity
import javax.inject.Inject

@PerActivity
class ScreenshotBlockExtension @Inject constructor() : ActivityExtension() {

    fun startBlockingScreenshots() {
        if (BuildConfig.PRODUCTION && Globals.isReleaseBuild()) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // prevent screenshots
        }
    }

    fun stopBlockingScreenshots() {
        if (BuildConfig.PRODUCTION && Globals.isReleaseBuild()) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}