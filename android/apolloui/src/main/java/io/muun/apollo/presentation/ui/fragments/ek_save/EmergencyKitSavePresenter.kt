package io.muun.apollo.presentation.ui.fragments.ek_save

import android.content.Intent
import android.os.Bundle
import io.muun.apollo.R
import io.muun.apollo.data.apis.DriveAuthenticator
import io.muun.apollo.data.apis.DriveError
import io.muun.apollo.data.apis.DriveFile
import io.muun.apollo.data.fs.FileCache
import io.muun.apollo.data.os.sharer.FileSharer
import io.muun.apollo.domain.action.ek.AddEmergencyKitMetadataAction
import io.muun.apollo.domain.action.ek.RenderEmergencyKitAction
import io.muun.apollo.domain.action.ek.UploadToDriveAction
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_DRIVE_TYPE
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_DRIVE
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_EMAIL
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_SAVE_OPTION
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_SAVE_SELECT
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_SHARE
import io.muun.apollo.presentation.export.PdfExportError
import io.muun.apollo.presentation.export.PdfExporter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import timber.log.Timber
import javax.inject.Inject

@PerFragment
class EmergencyKitSavePresenter @Inject constructor(
    private val fileCache: FileCache,
    private val renderEmergencyKit: RenderEmergencyKitAction,
    private val addEmergencyKitMetadata: AddEmergencyKitMetadataAction,
    private val uploadToDrive: UploadToDriveAction,
    private val driveAuthenticator: DriveAuthenticator

): SingleFragmentPresenter<EmergencyKitSaveView, EmergencyKitSaveParentPresenter>() {

    var isExportingPdf = false

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        renderEmergencyKit.state
            .compose(handleStates(null, this::handleError))
            .doOnNext(this::onRenderResult)
            .let(this::subscribeTo)

        addEmergencyKitMetadata.state
            .compose(handleStates(null, this::handleError))
            .doOnNext { onMetadataAdded() }
            .let(this::subscribeTo)

        uploadToDrive.state
            .compose(handleStates(view::setDriveUploading, this::handleError))
            .doOnNext(this::onUploadResult)
            .let(this::subscribeTo)
    }

    fun getDriveAuthenticator() =
        driveAuthenticator

    fun exportEmergencyKit() {
        // We want to avoid re-entry if we're already doing an export. This variable will be set
        // to `false` in `onPdfExportFinished`.
        if (isExportingPdf) {
            return
        }
        isExportingPdf = true

        // Cool, proceed:
        renderEmergencyKit.run()
    }

    fun goBack() {
        parentPresenter.cancelEmergencyKitSave()
    }

    fun reportSelection(option: EmergencyKitSaveOption) {
        val eventParam = when (option) {
            EmergencyKitSaveOption.SEND_EMAIL,
            EmergencyKitSaveOption.SEND_EMAIL_PICKER -> E_EK_SAVE_OPTION.EMAIL
            EmergencyKitSaveOption.SHARE_MANUALLY -> E_EK_SAVE_OPTION.MANUAL
            EmergencyKitSaveOption.SAVE_TO_DRIVE -> E_EK_SAVE_OPTION.DRIVE
        }

        analytics.report(E_EK_SAVE_SELECT(eventParam))
    }

    fun reportManualShareStarted(app: String?) {
        analytics.report(E_EK_SHARE(app ?: ""))
    }

    fun reportThirdPartyAppOpened() {
        parentPresenter.reportEmergencyKitShared() // we're not really sure, though
    }

    fun reportGoogleSignInStarted() {
        analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.SIGN_IN_START))
    }
    
    fun reportGoogleSignInComplete(resultIntent: Intent) {
        try {
            driveAuthenticator.getSignedInAccount(resultIntent) // called just for error checking
            analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.SIGN_IN_FINISH))

        } catch (e: Throwable) {
            analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.SIGN_IN_ERROR))
            handleError(e)
            return
        }

        analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.UPLOAD_START))
        uploadToDrive.run(fileCache.get(FileCache.Entry.EMERGENCY_KIT))
    }

    fun composeEmergencyKitEmail(): FileSharer.Email {
        val localFile = fileCache.get(FileCache.Entry.EMERGENCY_KIT)

        return FileSharer.Email(
            attachment = localFile.uri, // TODO: pick recipient, subject, etc.
            attachmentType = localFile.type
        )
    }

    private fun onRenderResult(kitGen: GeneratedEmergencyKit) {
        // Clear previously saved files:
        fileCache.delete(FileCache.Entry.EMERGENCY_KIT_NO_META)
        fileCache.delete(FileCache.Entry.EMERGENCY_KIT)

        // NOTE:
        // Clearing the files as above should not be necessary (though it's cleaner), but not
        // doing so triggers cryptic errors from deep inside the PDF library we use to attach PDF
        // metadata files, in a method called ParseObjectAttributes. If you encounter this error
        // and come here looking for answers, ask around before diving in.

        val file = fileCache.getFile(FileCache.Entry.EMERGENCY_KIT_NO_META)

        // TODO: we need to render, save and share the PDF. Now, a Presenter should NOT receive a
        // WebView instance (needed to render), and a Fragment should NOT handle storing the kit.
        // So who is responsible? Thorny issue. For now, this.
        val webView = view.pdfWebView

        val exporter = PdfExporter(webView, kitGen.html, file) {
            onPdfExportFinished(kitGen, it)
        }

        exporter.startSingleUse()
    }

    private fun onUploadResult(driveFile: DriveFile) {
        analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.UPLOAD_FINISH))
        parentPresenter.reportEmergencyKitUploaded(driveFile)
    }

    private fun onPdfExportFinished(kitGen: GeneratedEmergencyKit, error: PdfExportError?) {
        isExportingPdf = false

        if (error != null) {
            handleError(error)
            return
        }

        addEmergencyKitMetadata.run(kitGen.metadata)
    }

    private fun onMetadataAdded() {
        val localFile = fileCache.get(FileCache.Entry.EMERGENCY_KIT)
        view.onEmergencyKitExported(localFile)
    }

    override fun handleError(error: Throwable?) {
        if (error is DriveError) {
            Timber.e(error)
            analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.UPLOAD_ERROR, error.message ?: ""))
            view.setDriveError(error)

        } else {
            super.handleError(error)
        }
    }

    override fun getEntryEvent() =
        AnalyticsEvent.S_EMERGENCY_KIT_SAVE()

    fun reportEmailShareStarted(app: String) {
        analytics.report(E_EK_EMAIL(app))
    }

    fun reportGoogleSignInCanceled() {
        // This method can be called for two reasons that we know of:
        // 1. The user manually dismissed the Google SignIn dialog
        // 2. The user chose an account, but couldn't reach the Google API server

        // Here, we try to tell those two apart to provide some feedback, which the Google activity
        // doesn't do.

        val isConnected = networkInfoProvider.currentNetworkInfo
            .map { it.isConnected }
            .orElse(false)

        if (!isConnected) {
            view.showTextToast(context.getString(R.string.ek_save_drive_no_internet))
        }
    }
}