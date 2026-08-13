package io.muun.apollo.data.afs

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaDrm
import io.muun.apollo.data.os.OS
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import timber.log.Timber
import java.lang.reflect.Field
import java.util.UUID

private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

/**
 * Alternative DRM device ID acquisition.
 *
 * The configuration change is process-global and not atomic — any concurrent framework call
 * during MediaDrm construction (which can take tens of ms) may observe unexpected state on
 * another thread. This is an accepted trade-off given the narrow window and the value of the signal.
 *
 * If the context restore fails (extremely unlikely given reflection access was already proven),
 * the modified configuration stays installed permanently — logged as an error if it happens.
 */
class ContextSwapDrmFetcher(private val context: Context) {

    fun getDrmId(): String {
        val app = context.applicationContext
        val targetField = getMBaseField() ?: return Constants.ERROR

        val originalBase = try {
            targetField.get(app) as? Context
        } catch (_: Exception) {
            null
        } ?: return Constants.ERROR

        val wrapper = object : ContextWrapper(originalBase) {
            override fun getOpPackageName(): String = context.packageName
        }

        val drmContextSwap: MediaDrm
        try {
            targetField.set(app, wrapper)
            drmContextSwap = MediaDrm(WIDEVINE_UUID)
        } catch (_: Exception) {
            return Constants.ERROR
        } finally {
            try {
                targetField.set(app, originalBase)
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "drmContextSwap: failed to restore mBase — wrapper may be permanent"
                )
            }
        }

        return try {
            val idBytes = getSafeDeviceId(drmContextSwap) ?: return Constants.ERROR
            hashDeviceId(idBytes)
        } catch (_: Exception) {
            Constants.ERROR
        } finally {
            releaseMediaDrm(drmContextSwap)
        }
    }

    @SuppressLint("PrivateApi")
    private fun getMBaseField(): Field? {
        return try {
            ContextWrapper::class.java
                .getDeclaredField("mBase")
                .apply { isAccessible = true }
        } catch (_: Exception) {
            null
        }
    }

    private fun getSafeDeviceId(mediaDrm: MediaDrm): ByteArray? {
        return try {
            mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseMediaDrm(drmObject: MediaDrm) {
        if (OS.supportsMediaDrmClose()) {
            drmObject.close()
        } else {
            drmObject.release()
        }
    }

    private fun hashDeviceId(bytes: ByteArray): String =
        Encodings.bytesToHex(Hashes.sha256(bytes))
}
