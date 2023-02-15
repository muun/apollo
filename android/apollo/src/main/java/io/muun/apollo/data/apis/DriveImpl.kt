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
import com.google.api.services.drive.model.Revision
import io.muun.common.utils.Preconditions
import rx.Observable
import rx.schedulers.Schedulers
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
    val executor: Executor,
) :
    DriveAuthenticator,
    DriveUploader {

    companion object {
        const val DRIVE_FOLDER_TYPE = "application/vnd.google-apps.folder"
        const val DRIVE_FOLDER_NAME = "Muun"
        const val DRIVE_FOLDER_PARENT = "root"
    }

    private val signInClient by lazy {
        createSignInClient()
    }

    override fun getSignInIntent() =
        signInClient.signInIntent

    override fun getSignedInAccount(resultIntent: Intent?): GoogleSignInAccount {
        val completedTask = GoogleSignIn.getSignedInAccountFromIntent(resultIntent)

        // This is needlessly wrapped in a Task. If you check the implementation, you'll see it's
        // all perfectly synchronous. The Task instance is created from available data, much like
        // calling Observable.just(result) or Observable.error(exception).
        if (completedTask.isSuccessful) {
            return completedTask.result!!
        } else {
            throw DriveError(completedTask.exception!!)
        }
    }

    override fun upload(
        file: File,
        mimeType: String,
        uniqueProp: String,
        props: Map<String, String>,
    ): Observable<DriveFile> {

        val resultSubject = PublishSubject.create<DriveFile>()

        Tasks.call(executor) { executeUpload(file, mimeType, uniqueProp, props) }
            .addOnSuccessListener {
                resultSubject.onNext(it)
                resultSubject.onCompleted()
            }
            .addOnFailureListener {
                resultSubject.onError(DriveError(it))
                resultSubject.onCompleted()
            }

        return resultSubject.asObservable()
            .observeOn(Schedulers.from(executor)) // Task invokes callbacks in the main thread
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

    private fun executeUpload(
        file: File,
        mimeType: String,
        uniqueProp: String,
        props: Map<String, String>,
    ): DriveFile {

        Preconditions.checkArgument(props.containsKey(uniqueProp))

        val driveService = createDriveService()
        val folder = getExistingMuunFolder(driveService) ?: createNewMuunFolder(driveService)

        // We want to either update an existing file or create a new one. This depends on whether
        // we're reasonably sure that an existing entry is conceptually the same file as the one
        // we're uploading.
        val updateCandidates = getUpdateCandidates(driveService, file.name, mimeType, folder)
        val fileToUpdate = getFileToUpdate(updateCandidates, uniqueProp, props[uniqueProp]!!)

        return if (fileToUpdate != null) {
            updateFile(driveService, fileToUpdate, file, props, keepRevision = true)
        } else {
            createFile(driveService, mimeType, file, folder, props)
        }
    }

    private fun createFile(
        driveService: Drive,
        mimeType: String,
        file: File,
        folder: DriveFile,
        props: Map<String, String> = mapOf(),
    ): DriveFile {

        val metadata = FileMetadata()
            .setParents(listOf(folder.id))
            .setMimeType(mimeType)
            .setName(file.name)
            .setAppProperties(props)

        val content = FileContent(mimeType, file)

        return driveService.files()
            .create(metadata, content)
            .setFields("*") // populate all response fields (eg permalink, by default some are null)
            .execute()
            .let { toDriveFile(it, folder) }
    }

    private fun updateFile(
        driveService: Drive,
        existingFile: DriveFile,
        newContent: File,
        newProps: Map<String, String> = mapOf(),
        keepRevision: Boolean = false,
    ): DriveFile {

        if (keepRevision) {
            setKeepRevision(driveService, existingFile) // TODO: after 200 revisions, this will fail
        }

        val metadata = FileMetadata()
            .setAppProperties(newProps) // includes only the props we want to set

        val content = FileContent(existingFile.mimeType, newContent)

        return driveService.files()
            .update(existingFile.id, metadata, content)
            .setFields("*") // populate all response fields (eg permalink, by default some are null)
            .execute()
            .let { toDriveFile(it, existingFile.parent) }
    }

    private fun setKeepRevision(driveService: Drive, existingFile: DriveFile) {
        val revision = Revision()
        revision.keepForever = true

        driveService.revisions()
            .update(existingFile.id, existingFile.revisionId, revision)
            .execute()
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
        val query = sanitizeDriveQuery(
            """
            mimeType='$DRIVE_FOLDER_TYPE' and 
            name='$DRIVE_FOLDER_NAME' and 
            '$DRIVE_FOLDER_PARENT' in parents and
            trashed=false
            """
        )

        return driveService.files()
            .list()
            .setQ(query)
            .setFields("*") // populate all response fields (eg permalink, by default some are null)
            .execute()
            .files
            .getOrNull(0) // note: there could be multiple "/Muun" folders (yes), we'll pick 1st
            ?.let { toDriveFile(it) }
    }

    private fun getFileToUpdate(
        candidates: List<DriveFile>,
        uniqueProp: String,
        uniqueValue: String,
    ): DriveFile? {

        // If this is the only file, created before the addition of properties, pick it:
        if (candidates.size == 1 && !candidates[0].properties.containsKey(uniqueProp)) {
            return candidates[0]
        }

        // Otherwise, pick any file with a matching `uniqueProp` (or null):
        return candidates.find { it.properties[uniqueProp] == uniqueValue }
    }

    private fun getUpdateCandidates(
        driveService: Drive,
        name: String,
        mimeType: String,
        folder: DriveFile,
    ): List<DriveFile> {

        val query = sanitizeDriveQuery(
            """
            mimeType='$mimeType' and 
            name='$name' and 
            '${folder.id}' in parents and
            trashed=false
            """
        )

        // NOTE:
        // If there's more than 100 competing files (max results at the time of writing), we only
        // get those first 100. Good enough.

        return driveService.files()
            .list()
            .setQ(query)
            .setFields("*")
            .execute()
            .files
            .map { toDriveFile(it) }
    }

    private fun toDriveFile(metadata: FileMetadata, parentFile: DriveFile? = null) =
        DriveFile(
            metadata.id,
            metadata.headRevisionId ?: "", // folders don't have revisions. Oh well.
            metadata.name,
            metadata.mimeType,
            metadata.size,
            metadata.webViewLink,
            parentFile,
            metadata.appProperties ?: mapOf()
        )

    private fun sanitizeDriveQuery(query: String) =
        query.trimIndent().trim().replace("\n", " ")
}
