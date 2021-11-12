package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.apis.DriveFile
import io.muun.apollo.data.apis.DriveUploader
import io.muun.apollo.data.fs.LocalFile
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.model.EmergencyKitExport
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import io.muun.apollo.domain.model.user.User
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadToDriveAction @Inject constructor(
    private val userRepository: UserRepository,
    private val driveUploader: DriveUploader,
    private val reportEmergencyKitExported: ReportEmergencyKitExportedAction,
): BaseAsyncAction2<LocalFile, GeneratedEmergencyKit, DriveFile>() {

    companion object {
        val PROP_USER = "muun_user"
        val PROP_EK_VERSION = "muun_ek_version"
    }

    /**
     * Upload a file to Google Drive, assuming an account is signed in.
     */
    override fun action(localFile: LocalFile, ek: GeneratedEmergencyKit): Observable<DriveFile> =
        Observable
            .defer { userRepository.fetch() }
            .first()
            .flatMap {
                val props = mapOf(
                    PROP_USER to getUserPropValue(it),
                    PROP_EK_VERSION to getEkVersionValue(ek)
                )

                driveUploader.upload(localFile.toFile(), localFile.type, PROP_USER, props)
            }
            .flatMap { driveFile ->
                reportEmergencyKitExported.actionNow(
                    EmergencyKitExport(ek, true, EmergencyKitExport.Method.DRIVE)
                )

                Observable.just(driveFile)
            }


    /** Get the PROP_USER value, which is the hex-encoded hash of the stringified Houston ID */
    private fun getUserPropValue(user: User): String {
        val hidBytes = user.hid.toString().toByteArray(Charsets.UTF_8)
        val hidHash = Hashes.sha256(hidBytes)

        return Encodings.bytesToHex(hidHash)
    }

    /** Get the PROP_EK_VERSION value, which is the stringified version number */
    private fun getEkVersionValue(ek: GeneratedEmergencyKit): String {
        return ek.version.toString()
    }
}
