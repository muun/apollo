package io.muun.apollo.presentation.ui.fragments.ek_save

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.muun.apollo.R
import io.muun.apollo.data.apis.DriveAuthenticator
import io.muun.apollo.data.apis.DriveError
import io.muun.apollo.data.apis.DriveFile
import io.muun.apollo.data.fs.FileCache
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.action.ek.AddEmergencyKitMetadataAction
import io.muun.apollo.domain.action.ek.RenderEmergencyKitAction
import io.muun.apollo.domain.action.ek.UploadToDriveAction
import io.muun.apollo.domain.errors.SaveToDiskError
import io.muun.apollo.domain.model.FeedbackCategory
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.ERROR_TYPE
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_DRIVE_TYPE
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_DRIVE
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_SAVE_OPTION
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_SAVE_SELECT
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EK_SHARE
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EMERGENCY_KIT_CLOUD_FEEDBACK_SUBMIT
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_EMERGENCY_KIT_SAVE_TO_DISK
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_ERROR
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_EMERGENCY_KIT_CLOUD_FEEDBACK
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_EMERGENCY_KIT_MANUAL_ADVICE
import io.muun.apollo.presentation.export.PdfExportError
import io.muun.apollo.presentation.export.PdfExporter
import io.muun.apollo.presentation.export.SaveToDiskExporter
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
    private val driveAuthenticator: DriveAuthenticator,
    private val userActions: UserActions

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
            EmergencyKitSaveOption.SHARE_MANUALLY -> E_EK_SAVE_OPTION.MANUAL
            EmergencyKitSaveOption.SAVE_TO_DRIVE -> E_EK_SAVE_OPTION.DRIVE
        }

        analytics.report(E_EK_SAVE_SELECT(eventParam))
    }

    /**
     * There's a limitation here. Android OS doesn't allow us to know with 100% certainty that
     * the EK was successfully shared or saved locally. But this is our best effort, we accept
     * there will be false-positives. We signal that this step of the flow is completed and we can
     * move forward. Worst case, users can always press back and return.
     *
     * Note: we also do a best effort to try to identify the third party app chosen for the manual
     * export for tracking purposes (which is also apparently not 100% effective).
     */
    fun manualShareCompleted(chosenShareTarget: String?) {
        parentPresenter.confirmManualShareCompleted() // we're not really sure, though

        analytics.report(E_EK_SHARE(chosenShareTarget ?: ""))
    }

    fun reportGoogleSignInStarted() {
        analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.SIGN_IN_START))
    }
    
    fun reportGoogleSignInComplete(resultIntent: Intent?) {
        try {
            driveAuthenticator.getSignedInAccount(resultIntent) // called just for error checking
            analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.SIGN_IN_FINISH))

        } catch (e: Throwable) {
            analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.SIGN_IN_ERROR))
            handleError(e)
            return
        }

        val generatedEmergencyKit = parentPresenter.getGeneratedEmergencyKit()
        analytics.report(E_EK_DRIVE(E_DRIVE_TYPE.UPLOAD_START))
        uploadToDrive.run(fileCache.get(FileCache.Entry.EMERGENCY_KIT), generatedEmergencyKit)
    }

    fun reportCloudFeedback(cloudName: String) {
        analytics.report(E_EMERGENCY_KIT_CLOUD_FEEDBACK_SUBMIT(cloudName))
        userActions.submitFeedbackAction.run(FeedbackCategory.CLOUD_REQUEST, cloudName)
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
        parentPresenter.confirmEmergencyKitUploaded(driveFile)
    }

    private fun onPdfExportFinished(kitGen: GeneratedEmergencyKit, error: PdfExportError?) {
        isExportingPdf = false

        if (error != null) {
            handleError(error)
            return
        }

        parentPresenter.setGeneratedEmergencyKit(kitGen)

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

    fun reportCloudFeedbackOpen() {
        analytics.report(S_EMERGENCY_KIT_CLOUD_FEEDBACK())
    }

    fun reportManualAdviceOpen() {
        analytics.report(S_EMERGENCY_KIT_MANUAL_ADVICE())
    }

    fun saveToDiskSelected(treeUri: Uri) {
        try {
            analytics.report(E_EMERGENCY_KIT_SAVE_TO_DISK())
            val localFile = fileCache.get(FileCache.Entry.EMERGENCY_KIT)
            SaveToDiskExporter.saveToDisk(context, localFile.uri, treeUri)
        } catch (e: Exception) {
            handleError(SaveToDiskError(e))
            analytics.report(E_ERROR(ERROR_TYPE.EMERGENCY_KIT_SAVE_ERROR, e.toString()))
        }
    }
}