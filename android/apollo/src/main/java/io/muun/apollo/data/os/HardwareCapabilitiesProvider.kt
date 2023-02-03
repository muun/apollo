package io.muun.apollo.data.os

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.os.Environment
import android.provider.Settings
import io.muun.apollo.domain.errors.HardwareCapabilityError
import timber.log.Timber
import java.io.File
import java.lang.Exception
import javax.inject.Inject

private const val UNKNOWN = "UNKNOWN"

class HardwareCapabilitiesProvider @Inject constructor(private val context: Context) {

    private val actManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun getTotalRamInBytes(): Long {
        return try {
            val memInfo = MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem
        } catch (e: Exception) {
            Timber.e(HardwareCapabilityError("totalRam", e))
            -1
        }
    }

    fun getFreeRamInBytes(): Long {
        return try {
            val memInfo = MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.availMem
        } catch (e: Exception) {
            Timber.e(HardwareCapabilityError("freeRam", e))
            -1
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
        -1
    }

    private fun File.getFreeSpaceSafe() = try {
        freeSpace
    } catch (e: Exception) {
        Timber.e(HardwareCapabilityError("freeSpace", e))
        -1
    }
}