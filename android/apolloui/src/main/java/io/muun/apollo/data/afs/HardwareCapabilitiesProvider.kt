package io.muun.apollo.data.afs

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.media.MediaDrm
import android.os.Environment
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.os.OS
import io.muun.apollo.data.os.TorHelper
import io.muun.apollo.domain.errors.DrmProviderError
import io.muun.apollo.domain.errors.HardwareCapabilityError
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.math.abs

private const val UNKNOWN = "UNKNOWN"

private const val UNKNOWN_BYTES_AMOUNT = -1L

private const val BOOT_COUNT_UNSUPPORTED = -1
private const val BOOT_COUNT_ERROR = -2

/**
 * UUID for the W3C.
 * Identifier: 1077efec-c0b2-4d02-ace3-3c1e52e2fb4b.
 */
private val COMMON_PSSH_UUID = UUID(0x1077EFECC0B24D02L, -0x531cc3e1ad1d04b5L)

/**
 * UUID for the ClearKey DRM scheme.
 * ClearKey is supported on Android devices running Android 5.0 (API Level 21) and up.
 * Identifier: e2719d58-a985-b3c9-781a-b030af78d30e.
 */
private val CLEARKEY_UUID = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)

/**
 * UUID for the Widevine DRM scheme.
 * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
 * Identifier: edef8ba9-79d6-4ace-a3c8-27dcd51d21ed.
 */
private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

/**
 * UUID for the PlayReadv DRM scheme.
 * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
 * support it.
 * Identifier: 9a04f079-9840-4286-ab92-e65be0885f95.
 */
private val PLAYREADY_UUID = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)

class HardwareCapabilitiesProvider(private val context: Context) {

    private val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val widevineCache: WidevineCache by lazy { readWidevineCache() }

    @Suppress("ArrayInDataClass")
    private data class DrmIdResult(
        val hashedId: String,
        val rawBytes: ByteArray,
    )

    private data class WidevineCache(
        val securityLevel: String,
        val majorVersion: Int,
        val drmIdResult: DrmIdResult?,
    )

    private val knownDrmUuids =
        setOf(COMMON_PSSH_UUID, CLEARKEY_UUID, WIDEVINE_UUID, PLAYREADY_UUID)

    fun getDrmClientIds(): Map<String, String> {

        val drmProviderToClientId = HashMap<String, String>()

        saveClientIdForProviderIfExists(drmProviderToClientId, COMMON_PSSH_UUID)
        saveClientIdForProviderIfExists(drmProviderToClientId, CLEARKEY_UUID)
        // Widevine from cache
        widevineCache.drmIdResult?.let { result ->
            drmProviderToClientId[WIDEVINE_UUID.toString()] = result.hashedId
        }
        saveClientIdForProviderIfExists(drmProviderToClientId, PLAYREADY_UUID)

        if (OS.supportsGetSupportedCryptoSchemes()) {
            MediaDrm.getSupportedCryptoSchemes()
                // Exclude known UUIDs already handled explicitly above.
                .filter { it !in knownDrmUuids }
                .forEach { saveClientIdForProviderIfExists(drmProviderToClientId, it) }
        }

        return drmProviderToClientId
    }

    val totalRamInBytes: Long
        get() {
            return try {
                val memInfo = MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                memInfo.totalMem
            } catch (e: Exception) {
                Timber.e(HardwareCapabilityError("totalRam", e))
                UNKNOWN_BYTES_AMOUNT
            }
        }

    val totalInternalStorageInBytes: Long
        get() = Environment.getRootDirectory().getTotalSpaceSafe()

    val totalExternalStorageInBytes: List<Long>
        get() {
            val externalVolumeRootDirs: Array<File> = context.getExternalFilesDirs(null)
            return externalVolumeRootDirs.map { it.getTotalSpaceSafe() }
        }

    val androidId: String
        get() {
            return try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                Timber.e(HardwareCapabilityError("androidId", e))
                UNKNOWN
            }
        }

    val bootCountDiscrete: Int
        get() {
            val bc = bootCount()
            return if (bc > 0) {
                bucketWithLowRangeDetail(bc)
            } else {
                bc
            }
        }

    val glEsVersion: String
        get() {
            return try {
                activityManager.deviceConfigurationInfo.glEsVersion
            } catch (e: Exception) {
                Timber.e(HardwareCapabilityError("glEsVersion", e))
                UNKNOWN
            }
        }

    val bootOffset: Int
        get() {
            val bCount = bootCount()
            val bCycles = getBootCycles()

            if (bCount <= 0 || bCycles <= 0) {
                return Constants.INT_UNKNOWN
            }

            return bucketWithLowRangeDetail(abs(bCount - bCycles))
        }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun bootCount(): Int {
        if (!OS.supportsBootCountSetting()) {
            return BOOT_COUNT_UNSUPPORTED
        }

        return try {
            return Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (e: Exception) {
            Timber.e(HardwareCapabilityError("bootCount", e))
            BOOT_COUNT_ERROR
        }
    }

    val widevineSecurityLevel: String
        get() = widevineCache.securityLevel

    val widevineMajorVersion: Int
        get() = widevineCache.majorVersion

    val isPlainTextDrmId: Int
        get() {
            val result = widevineCache.drmIdResult ?: return Constants.INT_UNKNOWN

            return if (allBytesArePrintableAsciiOrNull(result.rawBytes)) {
                Constants.INT_PRESENT
            } else {
                Constants.INT_ABSENT
            }
        }

    private fun hashDeviceId(bytes: ByteArray): String =
        Encodings.bytesToHex(Hashes.sha256(bytes))

    /**
     * Raw keybox Device IDs are null-terminated C Language strings (32 bytes).
     * HMAC-SHA256 outputs have uniform byte distribution — virtually impossible for all bytes
     * to be printable ASCII or null.
     */
    private fun allBytesArePrintableAsciiOrNull(bytes: ByteArray): Boolean {
        return bytes.all { b ->
            val unsigned = b.toInt() and 0xFF
            unsigned == 0x00 || unsigned in 0x20..0x7E
        }
    }

    private fun File?.getTotalSpaceSafe() = try {
        this?.totalSpace ?: UNKNOWN_BYTES_AMOUNT
    } catch (e: Exception) {
        Timber.e(HardwareCapabilityError("totalSpace", e))
        UNKNOWN_BYTES_AMOUNT
    }

    private fun saveClientIdForProviderIfExists(map: HashMap<String, String>, providerUuid: UUID) {
        getDrmIdForProvider(providerUuid)?.let { result ->
            map[providerUuid.toString()] = result.hashedId
        }
    }

    private fun getDrmIdForProvider(drmProviderUuid: UUID): DrmIdResult? {
        try {

            if (!MediaDrm.isCryptoSchemeSupported(drmProviderUuid)) {
                return null
            }

            return getDrmIdFromClosableMediaDrm(drmProviderUuid)

        } catch (e: Exception) {
            // These two drm provider often return errors though they are listed as "supported"
            if (drmProviderUuid != COMMON_PSSH_UUID && drmProviderUuid != CLEARKEY_UUID) {
                Timber.e(DrmProviderError(drmProviderUuid, e))
            }
            return null
        }
    }

    /**
     * Abstracts basically what try-with-resources does, but since MediaDrm isn't AutoClosable in
     * all our supported Android versions, we need to do this manually/ad-hoc.
     * TODO: once our minSdk > 28 (OS.supportsMediaDrmClose()) we could do this with kotlin's
     *  try-with-resources.
     */
    private fun getDrmIdFromClosableMediaDrm(drmProviderUuid: UUID): DrmIdResult? {
        var mediaDrm: MediaDrm? = null
        try {
            mediaDrm = MediaDrm(drmProviderUuid)
            val deviceIdBytes = getSafeDeviceId(mediaDrm) ?: return null

            return DrmIdResult(
                hashDeviceId(deviceIdBytes),
                deviceIdBytes,
            )
        } finally {
            mediaDrm?.let(::releaseMediaDRM)
        }
    }

    private fun getSafeDeviceId(mediaDrm: MediaDrm): ByteArray? {
        return try {
            mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        } catch (e: Exception) {
            null
        }
    }

    private fun releaseMediaDRM(drmObject: MediaDrm) {
        if (OS.supportsMediaDrmClose()) {
            drmObject.close()
        } else {
            drmObject.release()
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun bucketWithLowRangeDetail(value: Int): Int {
        val step = 20
        val buckets = listOf(1, 2, 3, 6, 10, 15)
        return when {
            value < 1 -> value
            value < 20 -> buckets.firstOrNull { it >= value } ?: 20
            else -> ((value + (step - 1)) / step) * step
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getBootCycles(): Int {
        val bundle = try {
            context.contentResolver.call(
                Settings.Global.CONTENT_URI,
                TorHelper.process("TRG_tybony"),
                TorHelper.process("obbg_pbhag"),
                null
            )
                ?.takeIf { it.size() == 1 }
                ?: return Constants.INT_UNKNOWN
        } catch (e: Exception) {
            return Constants.INT_EXCEPTION
        }

        val key = bundle.keySet().first()
        bundle.getString(key)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        bundle.getInt(key).takeIf { it > 0 }?.let { return it }
        bundle.getLong(key).takeIf { it > 0 }?.toInt()?.let { return it }

        return Constants.INT_UNKNOWN
    }

    private fun readWidevineCache(): WidevineCache {
        var mediaDrm: MediaDrm? = null
        return try {
            mediaDrm = MediaDrm(WIDEVINE_UUID)

            val securityLevel = try {
                mediaDrm.getPropertyString("securityLevel")
            } catch (_: Exception) {
                Constants.ERROR
            }
            val majorVersion = try {
                mediaDrm.getPropertyString("version")
                    .substringBefore(".")
                    .toIntOrNull() ?: Constants.INT_UNKNOWN
            } catch (_: Exception) {
                Constants.INT_EXCEPTION
            }

            val drmId = try {
                getSafeDeviceId(mediaDrm)?.let { bytes ->
                    DrmIdResult(
                        hashDeviceId(bytes),
                        bytes,
                    )
                }
            } catch (_: Exception) {
                null
            }

            WidevineCache(securityLevel, majorVersion, drmId)
        } catch (_: Exception) {
            WidevineCache(Constants.ERROR, Constants.INT_EXCEPTION, null)
        } finally {
            mediaDrm?.let(::releaseMediaDRM)
        }
    }
}