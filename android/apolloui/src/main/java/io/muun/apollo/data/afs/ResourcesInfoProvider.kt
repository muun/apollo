package io.muun.apollo.data.afs

import android.content.Context
import android.util.DisplayMetrics
import kotlinx.serialization.Serializable


class ResourcesInfoProvider(private val context: Context) {

    /**
     * Structured Display Metrics data.
     */
    @Serializable
    data class DisplayMetricsInfo(
        val density: Float,
        val densityDpi: Int,
        val widthPixels: Int,
        val heightPixels: Int,
        val xdpi: Float,
        val ydpi: Float,
    )


    val displayMetrics: DisplayMetricsInfo
        get() {
            val dm: DisplayMetrics = context.applicationContext.resources.displayMetrics
            return DisplayMetricsInfo(
                dm.density,
                dm.densityDpi,
                dm.widthPixels,
                dm.heightPixels,
                dm.xdpi,
                dm.ydpi
            )
        }
}