package io.muun.apollo.data.afs

import android.content.Context
import android.os.Environment
import io.muun.apollo.data.os.TorHelper
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import java.io.RandomAccessFile

class FileInfoProvider(private val context: Context) {

    private val EXTERNAL_PATH = "/fgbentr/rzhyngrq/0"
    private val APP_EXTERNAL_PATH = "$EXTERNAL_PATH/Naqebvq"
    private val DEFAULT_DATE_PATH = "$APP_EXTERNAL_PATH/qngn/.abzrqvn"
    internal val APP_EXTERNAL_DIRS = listOf(
        "qngn",
        "boo",
        "zrqvn"
    )

    val quickEmProps: Int
        get() {
            val emPaths = listOf(
                "/flfgrz/ova/drzh-cebcf",
                "/qri/drzh_cvcr",
                "/qri/fbpxrg/drzhq",
                "/vavg.enapuh.ep",
                "/vavg.tbyqsvfu.ep",
                "/flf/drzh_genpr",
                "/flfgrz/yvo/yvop_znyybp_qroht_drzh.fb",
                "/flfgrz/ova/drzhq",
                "/flf/pynff/zvfp/drzhq",
                "/flf/pynff/zvfp/drzh_cvcr"
            )

            val found = emPaths.any { File(TorHelper.process(it)).exists() }
            return if (found) {
                Constants.INT_PRESENT
            } else {
                Constants.INT_ABSENT
            }
        }

    val emArchitecture: Int
        get() {
            val fileNames = listOf(
                TorHelper.process("yvo/yvop.fb"),
                TorHelper.process("yvo/yvop64.fb"),
                TorHelper.process("yvo/yvoz.fb"),
                TorHelper.process("yvo64/yvop.fb"),
            )
            val fileToAccess = findExistingFile(fileNames) ?: return Constants.INT_UNKNOWN
            try {
                RandomAccessFile(fileToAccess, "r").use { file ->
                    file.seek(0x12)
                    val buf = ByteArray(2)
                    file.readFully(buf)
                    file.close()
                    //The byte `buf[0]` is the least significant byte and
                    //the byte `buf[1]` is the most significant byte.
                    return buf[0].toInt() and 0xff or (buf[1].toInt() and 0xff shl 8)
                }
            } catch (e: Exception) {
                Timber.e("emArchitecture", e)
                return Constants.INT_EXCEPTION
            }
        }

    val appSize: Long
        get() {
            val file = File(context.applicationInfo.sourceDir)
            return if (file.exists()) {
                file.length()
            } else {
                Constants.INT_UNKNOWN.toLong()
            }
        }

    val defaultDate: Long
        get() = getFsLastModDate(DEFAULT_DATE_PATH)

    val androidDate: Long
        get() = getFsLastModDate(APP_EXTERNAL_PATH)

    val hasNewEntriesInAppExternalStorage: Int
        get() =
            getFilesFromDir(TorHelper.process(APP_EXTERNAL_PATH))?.let { appExternalFiles ->
                if (appExternalNewEntries(appExternalFiles)) {
                    Constants.INT_PRESENT
                } else {
                    Constants.INT_ABSENT
                }
            } ?: Constants.INT_UNKNOWN


    val externalMinDate: Long
        get() {
            val appExternalFiles = getFilesFromDir(TorHelper.process(APP_EXTERNAL_PATH))
                ?: return Constants.LONG_UNKNOWN
            val externalFiles = getFilesFromDir(TorHelper.process(EXTERNAL_PATH))
                ?: return Constants.LONG_UNKNOWN

            val allFiles: Array<File> = externalFiles.plus(appExternalFiles)
            val default = defaultDate
            return allFiles
                .map { AfsUtils.epochAtUtcMidnight(it.lastModified()) }
                .filter { it != default }
                .minOrNull()
                ?: default
        }

    // Compare last modification Time (lastModified) across directories.
    // With 0 or 1 directory, no comparison is possible, so we return UNKNOWN
    val hasUniqueBaseDateInExternalStorage: Int
        get() {
            val base = File(TorHelper.process(EXTERNAL_PATH))
            if (!base.exists() || !base.isDirectory) {
                return Constants.INT_UNKNOWN
            }

            val directories = base.listFiles(FileFilter { it.isDirectory })
                ?.takeIf { it.size >= 2 }
                ?: return Constants.INT_UNKNOWN

            val uniqueDates = directories
                .map { AfsUtils.epochAtUtcMidnight(it.lastModified()) }
                .toSet() // groups by days

            return if (uniqueDates.size == 1) {
                Constants.INT_PRESENT
            } else {
                Constants.INT_ABSENT
            }
        }

    private fun findExistingFile(fileNames: List<String>): File? {
        for (fileName in fileNames) {
            val file = File(Environment.getRootDirectory(), fileName)
            if (file.exists()) {
                return file
            }
        }
        return null
    }

    private fun getFsLastModDate(path: String): Long {
        val file = File(TorHelper.process(path))
        val lastMod = file.takeIf { it.exists() }?.lastModified()
        return lastMod?.let { AfsUtils.epochAtUtcMidnight(it) } ?: Constants.LONG_UNKNOWN
    }

    // Visible for test
    fun getFilesFromDir(path: String): Array<File>? {
        val pathFile = File(path)
        return try {
            if (pathFile.exists() && pathFile.isDirectory) {
                pathFile.listFiles()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun appExternalNewEntries(entries: Array<File>): Boolean {
        if (entries.any { it.isFile }) {
            return true
        }
        val allowed = APP_EXTERNAL_DIRS.map { TorHelper.process(it) }
        return entries.any { it.name !in allowed }
    }
}