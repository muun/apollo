package io.muun.apollo.presentation.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.muun.apollo.data.fs.FileCache
import java.io.InputStream
import java.io.OutputStream

object SaveToDiskExporter {

    private const val MIME_TYPE = "application/pdf"

    @Throws(Exception::class)
    fun saveToDisk(context: Context, sourceUri: Uri, destinationUri: Uri) {

        // Get directory from uri tree selected by user
        val targetDocumentDir = DocumentFile.fromTreeUri(context, destinationUri)

        // Create new file in the location
        // If the file or any stream is null an exception is throw to handle a possible error
        val file = checkNotNull(targetDocumentDir?.createFile(MIME_TYPE, FileCache.Entry.EMERGENCY_KIT.fileName))
        val source: InputStream = checkNotNull(context.contentResolver.openInputStream(sourceUri))
        val destination: OutputStream = checkNotNull(context.contentResolver.openOutputStream(file.uri))

        // copy from cache uri to new file created
        val byteBuffer = ByteArray(4096)
        var readBytes: Int
        while (source.read(byteBuffer).also { readBytes = it } != -1) {
            destination.write(byteBuffer, 0, readBytes)
        }
        destination.flush()
        destination.close()
        source.close()

    }
}