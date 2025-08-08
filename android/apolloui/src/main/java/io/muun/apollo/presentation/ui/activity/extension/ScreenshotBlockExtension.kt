package io.muun.apollo.presentation.ui.activity.extension

import android.view.WindowManager
import io.muun.apollo.data.external.Globals
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.di.PerActivity
import timber.log.Timber
import javax.inject.Inject

@PerActivity
class ScreenshotBlockExtension @Inject constructor() : ActivityExtension() {

    fun startBlockingScreenshots(caller: String) {
        if (Globals.INSTANCE.isProduction && Globals.INSTANCE.isRelease) {
            Timber.i("blockscreenshots: $caller ${activity.javaClass.simpleName} START")
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // prevent screenshots
        }
    }

    fun stopBlockingScreenshots(caller: String) {
        if (Globals.INSTANCE.isProduction && Globals.INSTANCE.isRelease) {
            Timber.i("blockscreenshots: $caller ${activity.javaClass.simpleName} STOP")
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}