package io.muun.apollo.utils

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.IOException

object adb {

    private val BUFFER_SIZE = 8192

    fun exec(vararg cmd: String): String {
        val cmdLine = shellJoin(*cmd)

        Log.d("ADB", "Cmd: $cmdLine")
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(cmdLine)

        val baos = ByteArrayOutputStream()
        writeDataToByteStream(fd!!, baos)

        val output = String(baos.toByteArray())
        Log.d("ADB", "Out: $output")

        return output
    }

    operator fun invoke(vararg cmd: String): String {
        return exec(*cmd)
    }

    /**
     * Method to write data into byte array
     *
     * @param pfDescriptor Used to read the content returned by shell command
     * @param outputStream Write the data to this output stream read from pfDescriptor
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun writeDataToByteStream(
        pfDescriptor: ParcelFileDescriptor, outputStream: ByteArrayOutputStream,
    ) {
        val inputStream = ParcelFileDescriptor.AutoCloseInputStream(pfDescriptor)
        try {
            val buffer = ByteArray(BUFFER_SIZE)
            var len = inputStream.read(buffer)
            while (len >= 0) {
                outputStream.write(buffer, 0, len)
                len = inputStream.read(buffer)
            }
        } finally {
            inputStream.close()
        }
    }

    private fun shellJoin(vararg words: String) = words.joinToString(" ")
}