package io.muun.apollo.presentation.app

import android.os.Build
import android.os.StrictMode
import io.muun.apollo.data.external.Globals

/**
 * Check out https://developer.android.com/reference/android/os/StrictMode.
 * We can use this developer tool to detect things mistakes or issue that can worsen UX (e.g disk
 * writes or network calls from UI, activity or sqlite object leaks, non-sdk api usage, etc...).
 */
object StrictMode {

    fun init() {
        if (Globals.INSTANCE.isDebug) {

            // https://developer.android.com/reference/android/os/StrictMode.ThreadPolicy.Builder
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
//                    TODO fix this
//                    .permitDiskReads()    // allow for now, we do it plenty in App#OnCreate
//                    .permitDiskWrites()   // allow for now, we do it plenty in App#OnCreate
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .penaltyDropBox()
//                    .penaltyDialog()  // too aggressive for now, even for debug
//                    .penaltyDeath()   // too aggressive for now, even for debug
                    .build()
            )

            // https://developer.android.com/reference/android/os/StrictMode.VmPolicy.Builder
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .customDetect()
                    .penaltyLog()
                    .penaltyDropBox()
//                    we can put upper bound for instance num of specific class, help detect leaks
//                    .setClassInstanceLimit(kclass, instanceLimit)
                    .build()
            )
        }
    }

    /**
     * Customized detection, based on { @link android.os.StrictMode.VmPolicy.Builder.detectAll },
     * which is final, and uses many private or hidden methods. Main hurdle is
     * #detectUntaggedSockets() which is super noisy (and useless?). As the rest of this class,
     * leaving some commented out code to explicitly state our stand regarding it.
     */
    private fun StrictMode.VmPolicy.Builder.customDetect(): StrictMode.VmPolicy.Builder {
        detectLeakedSqlLiteObjects()
        detectActivityLeaks()
        detectLeakedClosableObjects()
        detectLeakedRegistrationObjects()
        detectFileUriExposure()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            detectContentUriWithoutPermission()
//            Removing as requires silly workaround (https://github.com/square/okhttp/issues/3537)
//            detectUntaggedSockets()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            detectCredentialProtectedWhileLocked()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            detectUnsafeIntentLaunch()
            detectIncorrectContextUse()
        }

        return this
    }
}