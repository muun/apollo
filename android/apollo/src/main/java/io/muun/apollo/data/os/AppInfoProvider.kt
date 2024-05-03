package io.muun.apollo.data.os

import android.content.Context
import javax.inject.Inject

class AppInfoProvider @Inject constructor(private val context: Context) {
    val appDatadir: String
        get() {
            return context.applicationInfo.dataDir
        }
}