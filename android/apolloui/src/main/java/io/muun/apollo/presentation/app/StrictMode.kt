package io.muun.apollo.presentation.app

import io.muun.apollo.data.external.Globals

/**
 * Check out https://developer.android.com/reference/android/os/StrictMode.
 * We can use this developer tool to detect things mistakes or issue that can worsen UX (e.g disk
 * writes or network calls from UI, activity or sqlite object leaks, non-sdk api usage, etc...).
 */
object StrictMode {

    fun init() {
        if (Globals.INSTANCE.isDebugBuild) {

            // https://developer.android.com/reference/android/os/StrictMode.ThreadPolicy.Builder
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .permitDiskReads() // allow for now, we do it plenty in App#OnCreate TODO fix it
                    .permitDiskWrites()// allow for now, we do it plenty in App#OnCreate TODO fix it
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .penaltyDialog()
                    .penaltyDropBox()
                    .penaltyDeath()
                    .build()
            )

            // https://developer.android.com/reference/android/os/StrictMode.VmPolicy.Builder
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
//                    we can put upper bound for instance num of specific class, help detect leaks
//                    .setClassInstanceLimit(kclass, instanceLimit)
                    .build()
            )
        }
    }
}