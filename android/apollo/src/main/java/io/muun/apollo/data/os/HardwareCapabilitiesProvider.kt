package io.muun.apollo.data.os

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.media.MediaDrm
import android.os.Environment
import android.os.UserManager
import android.provider.Settings
import io.muun.apollo.domain.errors.HardwareCapabilityError
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject

private const val UNKNOWN = "UNKNOWN"

private const val USER_CREATION_DATE_UNSUPPORTED = -1L
private const val USER_CREATION_DATE_NO_PROFILES = -2L
private const val USER_CREATION_DATE_READ_ERROR = -3L

private const val UNKNOWN_BYTES_AMOUNT = -1L

/**
 * UUID for the W3C
 */
private val COMMON_PSSH_UUID = UUID(0x1077EFECC0B24D02L, -0x531cc3e1ad1d04b5L)

/**
 * UUID for the ClearKey DRM scheme.
 * ClearKey is supported on Android devices running Android 5.0 (API Level 21) and up.
 */
private val CLEARKEY_UUID = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)

/**
 * UUID for the Widevine DRM scheme.
 * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
 */
private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

/**
 * UUID for the PlayReadv DRM scheme.
 * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
 */
private val PLAYREADY_UUID = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)

class HardwareCapabilitiesProvider @Inject constructor(private val context: Context) {

    private val actManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    fun getDrmClientIds(): List<String> {
        return listOf(
            getDrmIdForProvider(COMMON_PSSH_UUID),
            getDrmIdForProvider(CLEARKEY_UUID),
            getDrmIdForProvider(WIDEVINE_UUID),
            getDrmIdForProvider(PLAYREADY_UUID),
        )
    }

    fun getCreationTimestampInMilliseconds(): Long {

        if (OS.supportsUserCreationTime()) {
            try {
                for (userProfile in userManager.userProfiles) {
                    return userManager.getUserCreationTime(userProfile)
                }

                // No profiles
                return USER_CREATION_DATE_NO_PROFILES
            } catch (e: Exception) {
                Timber.e(HardwareCapabilityError("user creation date", e))
                return USER_CREATION_DATE_READ_ERROR
            }
        }

        // Can't get it
        return USER_CREATION_DATE_UNSUPPORTED
    }

    fun getTotalRamInBytes(): Long {
        return try {
            val memInfo = MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem
        } catch (e: Exception) {
            Timber.e(HardwareCapabilityError("totalRam", e))
            UNKNOWN_BYTES_AMOUNT
        }
    }

    fun getFreeRamInBytes(): Long {
        return try {
            val memInfo = MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.availMem
        } catch (e: Exception) {
            Timber.e(HardwareCapabilityError("freeRam", e))
            UNKNOWN_BYTES_AMOUNT
        }
    }

    fun getTotalInternalStorageInBytes(): Long =
        Environment.getRootDirectory().getTotalSpaceSafe()

    fun getFreeInternalStorageInBytes(): Long =
        Environment.getRootDirectory().getFreeSpaceSafe()

    fun getTotalExternalStorageInBytes(): List<Long> {
        val externalVolumeRootDirs: Array<File> = context.getExternalFilesDirs(null)
        return externalVolumeRootDirs.map { it.getTotalSpaceSafe() }
    }

    fun getFreeExternalStorageInBytes(): List<Long> {
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

    private fun File.getTotalSpaceSafe() = try {
        totalSpace
    } catch (e: Exception) {
        Timber.e(HardwareCapabilityError("totalSpace", e))
        UNKNOWN_BYTES_AMOUNT
    }

    private fun File.getFreeSpaceSafe() = try {
        freeSpace
    } catch (e: Exception) {
        Timber.e(HardwareCapabilityError("freeSpace", e))
        UNKNOWN_BYTES_AMOUNT
    }

    private fun getDrmIdForProvider(drmProviderUuid: UUID): String =
        try {
            val mediaDrm = MediaDrm(drmProviderUuid)
            val deviceIdBytes = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            Encodings.bytesToHex(Hashes.sha256(deviceIdBytes))
        } catch (e: Exception) {
            Timber.e(HardwareCapabilityError("mediaDRM", e))
            UNKNOWN
        }
}