package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.apis.DriveFile
import io.muun.apollo.data.apis.DriveUploader
import io.muun.apollo.data.fs.LocalFile
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.libwallet.LibwalletBridge
import rx.Observable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadToDriveAction @Inject constructor(
    private val driveUploader: DriveUploader

): BaseAsyncAction1<LocalFile, DriveFile>() {

    /**
     * Upload a file to Google Drive, assuming an account is signed in.
     */
    override fun action(localFile: LocalFile): Observable<DriveFile> =
        Observable.defer {
            driveUploader.upload(localFile.toFile(), localFile.type)
        }

}
