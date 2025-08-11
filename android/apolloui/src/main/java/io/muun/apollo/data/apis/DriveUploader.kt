package io.muun.apollo.data.apis

import android.content.Context
import rx.Observable
import java.io.File

/**
 * An interface to use DriveImpl without leaking Intents to the domain layer.
 */
interface DriveUploader {

    fun open(activityContext: Context, driveFile: DriveFile)

    fun upload(file: File, mimeType: String, uniqueProp: String, props: Map<String, String>):
        Observable<DriveFile>
}