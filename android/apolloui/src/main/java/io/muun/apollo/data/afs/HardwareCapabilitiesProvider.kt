package io.muun.apollo.data.afs

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.os.Environment
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import io.muun.apollo.data.os.OS
import io.muun.apollo.domain.errors.DrmProviderError
import io.muun.apollo.domain.errors.HardwareCapabilityError
import io.muun.apollo.domain.errors.SystemUserCreationDateError
import io.muun.apollo.domain.model.SystemUserInfo
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import timber.log.Timber
import java.io.File
import java.net.NetworkInterface
import java.util.*

private const val UNKNOWN = "UNKNOWN"

private const val USER_CREATION_DATE_UNSUPPORTED = -1L
private const val USER_CREATION_DATE_NO_PROFILES = -2L
private const val USER_CREATION_DATE_READ_ERROR = -3L

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

    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    fun getDrmClientIds(): Map<String, String> {

        val drmProviderToClientId = HashMap<String, String>()

        saveClientIdForProviderIfExists(drmProviderToClientId, COMMON_PSSH_UUID)
        saveClientIdForProviderIfExists(drmProviderToClientId, CLEARKEY_UUID)
        saveClientIdForProviderIfExists(drmProviderToClientId, WIDEVINE_UUID)
        saveClientIdForProviderIfExists(drmProviderToClientId, PLAYREADY_UUID)

        if (OS.supportsGetSupportedCryptoSchemes()) {

            val supportedCryptoSchemes = MediaDrm.getSupportedCryptoSchemes()

            supportedCryptoSchemes.forEach { drmProviderUuid ->
                saveClientIdForProviderIfExists(drmProviderToClientId, drmProviderUuid)
            }
        }

        return drmProviderToClientId
    }

    fun getSystemUsersInfo(): List<SystemUserInfo> {

        if (OS.supportsUserCreationTime()) {

            val isSystemUser = userManager.isSystemUser
            if (userManager.userProfiles.isEmpty()) {
                return listOf(SystemUserInfo(USER_CREATION_DATE_NO_PROFILES, isSystemUser))
            }

            val result = ArrayList<SystemUserInfo>()

            for (up in userManager.userProfiles) {
                result.add(SystemUserInfo(getCreationTimestampInMilliseconds(up), isSystemUser))
            }

            return result
        }

        // Can't get it
        return listOf(SystemUserInfo(USER_CREATION_DATE_UNSUPPORTED, false))
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getCreationTimestampInMilliseconds(userProfile: UserHandle): Long {
        return try {
            userManager.getUserCreationTime(userProfile)

        } catch (e: Exception) {
            Timber.e(SystemUserCreationDateError(userProfile, userManager.isSystemUser, e))
            USER_CREATION_DATE_READ_ERROR
        }
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

    val freeRamInBytes: Long
        get() {
            return try {
                val memInfo = MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                memInfo.availMem
            } catch (e: Exception) {
                Timber.e(HardwareCapabilityError("freeRam", e))
                UNKNOWN_BYTES_AMOUNT
            }
        }

    val totalInternalStorageInBytes: Long
        get() = Environment.getRootDirectory().getTotalSpaceSafe()

    val freeInternalStorageInBytes: Long
        get() = Environment.getRootDirectory().getFreeSpaceSafe()

    val totalExternalStorageInBytes: List<Long>
        get() {
            val externalVolumeRootDirs: Array<File> = context.getExternalFilesDirs(null)
            return externalVolumeRootDirs.map { it.getTotalSpaceSafe() }
        }

    val freeExternalStorageInBytes: List<Long>
        get() {
            val externalVolumeRootDirs: Array<File> = context.getExternalFilesDirs(null)
            return externalVolumeRootDirs.map { it.getFreeSpaceSafe() }
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

    val bootCount: Int
        get() {
            if (!OS.supportsBootCountSetting()) {
                return BOOT_COUNT_UNSUPPORTED
            }

            return try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            } catch (e: Exception) {
                Timber.e(HardwareCapabilityError("bootCount", e))
                BOOT_COUNT_ERROR
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


    val hardwareAddresses: List<String>
        get() {
            if (!OS.supportsHardwareAddresses()) {
                return emptyList()
            }
            return try {
                NetworkInterface.getNetworkInterfaces()?.asSequence()?.toList()
                    ?.filter { it.hardwareAddress != null }
                    ?.filter {
                        it.displayName.startsWith("wlan") ||
                            it.displayName.startsWith("p2p")
                    }
                    ?.map { networkInterface ->
                        networkInterface.hardwareAddress
                            .joinToString("-") { byte ->
                                "%02X".format(byte)
                            }
                    }
                    ?.sortedBy { it }?.toList() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun File?.getTotalSpaceSafe() = try {
        this?.totalSpace ?: UNKNOWN_BYTES_AMOUNT
    } catch (e: Exception) {
        Timber.e(HardwareCapabilityError("totalSpace", e))
        UNKNOWN_BYTES_AMOUNT
    }

    private fun File?.getFreeSpaceSafe() = try {
        this?.freeSpace ?: UNKNOWN_BYTES_AMOUNT
    } catch (e: Exception) {
        Timber.e(HardwareCapabilityError("freeSpace", e))
        UNKNOWN_BYTES_AMOUNT
    }

    private fun saveClientIdForProviderIfExists(map: HashMap<String, String>, providerUuid: UUID) {
        getDrmIdForProvider(providerUuid)?.let { drmId ->
            map[providerUuid.toString()] = drmId
        }
    }

    private fun getDrmIdForProvider(drmProviderUuid: UUID): String? {
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
    private fun getDrmIdFromClosableMediaDrm(drmProviderUuid: UUID): String? {
        var mediaDrm: MediaDrm? = null
        try {
            mediaDrm = MediaDrm(drmProviderUuid)
            val deviceIdBytes = getSafeDeviceId(mediaDrm) ?: return null

            return Encodings.bytesToHex(Hashes.sha256(deviceIdBytes))
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
}