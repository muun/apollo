package io.muun.apollo.data.apis

import android.app.Activity
import android.content.Context
import rx.Observable
import java.io.File

/**
 * An interface to use DriveImpl without leaking Intents to the domain layer.
 */
interface DriveUploader {

    fun upload(file: File, mimeType: String): Observable<DriveFile>

    fun open(activityContext: Context, driveFile: DriveFile)

}