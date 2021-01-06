package io.muun.apollo.data.apis

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import rx.Observable
import rx.subjects.PublishSubject
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

// FileMetadata removes the need for fully-qualified paths for `File`, which is also a Java stdlib
// class. The Drive variant, by the way, is actually a collection of metadata.
typealias FileMetadata = com.google.api.services.drive.model.File


@Singleton
class DriveImpl @Inject constructor(
    val context: Context,
    val executor: Executor
):
    DriveAuthenticator,
    DriveUploader
{

    companion object {
        val DRIVE_FOLDER_TYPE = "application/vnd.google-apps.folder"
        val DRIVE_FOLDER_NAME = "Muun"
        val DRIVE_FOLDER_PARENT = "root"
    }

    private val signInClient by lazy {
        createSignInClient()
    }

    override fun getSignInIntent() =
        signInClient.signInIntent

    override fun getSignedInAccount(result: Intent): GoogleSignInAccount {
        val completedTask = GoogleSignIn.getSignedInAccountFromIntent(result)

        // This is needlessly wrapped in a Task. If you check the implementation, you'll see it's
        // all perfectly synchronous. The Task instance is created from available data, much like
        // calling Observable.just(result) or Observable.error(exception).
        if (completedTask.isSuccessful) {
            return completedTask.result!!
        } else {
            throw DriveError(completedTask.exception!!)
        }
    }

    override fun upload(file: File, mimeType: String): Observable<DriveFile> {
        val resultSubject = PublishSubject.create<DriveFile>()

        Tasks.call(executor, { executeUpload(file, mimeType) })
            .addOnSuccessListener {
                resultSubject.onNext(it)
                resultSubject.onCompleted()
            }
            .addOnFailureListener {
                resultSubject.onError(DriveError(it))
                resultSubject.onCompleted()
            }

        return resultSubject.asObservable()
    }

    override fun open(activityContext: Context, driveFile: DriveFile) {
        // Link to the parent directory, or the file itself otherwise:
        val link = driveFile.parent?.link ?: driveFile.link

        // This Intent will launch the Drive app, or if that's not available (very unlikely) a
        // browser page that has pretty much the same UI.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))

        activityContext.startActivity(intent)
    }

    override fun signOut() {
        signInClient.revokeAccess().continueWith { signInClient.signOut() }.addOnCompleteListener {
            Timber.d("Logged out")
        }
    }

    private fun createDriveService(): Drive {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))

        credential.selectedAccount = GoogleSignIn.getLastSignedInAccount(context)!!.account

        val transport = AndroidHttp.newCompatibleTransport()

        return Drive.Builder(transport, GsonFactory(), credential)
            .setApplicationName("Muun")
            .build()
    }

    private fun createSignInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder()
            .requestScopes(Scope(Scopes.DRIVE_FILE))
            .requestEmail()
            .build()

        return GoogleSignIn.getClient(context, options)
    }

    private fun executeUpload(file: File, mimeType: String): DriveFile {
        val driveService = createDriveService()

        return createFile(driveService, mimeType, file)
    }

    private fun createFile(driveService: Drive, mimeType: String, file: File): DriveFile {
        val parentFolder = getExistingMuunFolder(driveService) ?: createNewMuunFolder(driveService)

        val metadata = FileMetadata()
            .setParents(listOf(parentFolder.id))
            .setMimeType(mimeType)
            .setName(file.name)

        val content = FileContent(mimeType, file)

        return driveService.files()
            .create(metadata, content)
            .setFields("*") // populate all response fields (eg permalink, by default some are null)
            .execute()
            .let { toDriveFile(it, parentFolder) }
    }

    private fun createNewMuunFolder(driveService: Drive): DriveFile {
        val metadata = FileMetadata()
            .setParents(listOf(DRIVE_FOLDER_PARENT))
            .setMimeType(DRIVE_FOLDER_TYPE)
            .setName(DRIVE_FOLDER_NAME)

        return driveService.files()
            .create(metadata)
            .setFields("*") // populate all response fields (eg permalink, by default some are null)
            .execute()
            .let { toDriveFile(it) }
    }

    private fun getExistingMuunFolder(driveService: Drive): DriveFile? {
        val query = sanitizeDriveQuery("""
            mimeType='$DRIVE_FOLDER_TYPE' and 
            name='$DRIVE_FOLDER_NAME' and 
            '$DRIVE_FOLDER_PARENT' in parents and
            trashed=false
        """)

        return driveService.files()
            .list()
            .setQ(query)
            .setFields("*") // populate all response fields (eg permalink, by default some are null)
            .execute()
            .files
            .getOrNull(0) // note: there could be multiple "/Muun" folders (yes), we'll pick 1st
            ?.let { toDriveFile(it) }
    }

    private fun toDriveFile(metadata: FileMetadata, parentFile: DriveFile? = null) =
        DriveFile(
            metadata.id,
            metadata.name,
            metadata.size,
            metadata.webViewLink,
            parentFile
        )

    private fun sanitizeDriveQuery(query: String) =
        query.trimIndent().trim().replace("\n", " ")
}
