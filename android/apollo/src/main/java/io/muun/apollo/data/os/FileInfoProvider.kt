package io.muun.apollo.data.os

import android.content.Context
import android.os.Environment
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

class FileInfoProvider @Inject constructor(private val context: Context) {

    val quickEmProps: Int
        get() {
            return if (File(TorHelper.process("/flfgrz/ova/drzh-cebcf")).exists()) {
                Constants.PRESENT
            } else {
                Constants.ABSENT
            }
        }

    val emArchitecture: Int
        get() {
            val fileNames = listOf(
                TorHelper.process("yvo/yvop.fb"),
                TorHelper.process("yvo/yvop64.fb"),
                TorHelper.process("yvo/yvoz.fb"),
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

    private fun findExistingFile(fileNames: List<String>): File? {
        for (fileName in fileNames) {
            val file = File(Environment.getRootDirectory(), fileName)
            if (file.exists()) {
                return file
            }
        }
        return null
    }
}