package io.muun.apollo.presentation.ui.utils

import android.os.Bundle
import android.os.Parcel
import timber.log.Timber

object BundleSizeLogger {

    private const val TAG = "BundleSize"
    private const val MIN_KEY_SIZE_BYTES = 1024

    fun logBundleBreakdown(label: String, bundle: Bundle) {
        val totalSize = measureBundleSize(bundle)
        Timber.tag(TAG).d("%s total: %dB", label, totalSize)

        bundle.keySet()
            .map { key -> key to measureKeySize(bundle, key) }
            .filter { it.second > MIN_KEY_SIZE_BYTES }
            .sortedByDescending { it.second }
            .forEach { (key, size) ->
                Timber.tag(TAG).d("  %s key '%s': %dB", label, key, size)
            }
    }

    private fun measureBundleSize(bundle: Bundle): Int {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            return parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun measureKeySize(source: Bundle, key: String): Int {
        val single = Bundle()
        val value = source.get(key) ?: return 0
        when (value) {
            is Bundle -> single.putBundle(key, value)
            is android.os.Parcelable -> single.putParcelable(key, value)
            is String -> single.putString(key, value)
            is Int -> single.putInt(key, value)
            is Boolean -> single.putBoolean(key, value)
            is java.io.Serializable -> single.putSerializable(key, value)
            else -> return 0
        }
        return measureBundleSize(single)
    }
}
