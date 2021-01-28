package io.muun.apollo.presentation.ui.fragments.ek_save

import android.app.Activity
import android.content.Intent
import android.view.View
import android.webkit.WebView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.data.fs.LocalFile
import io.muun.apollo.data.os.sharer.FileSharer
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.DrawerDialogFragment
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.MuunSaveOption
import io.muun.apollo.presentation.ui.view.MuunSaveOptionLabel
import rx.functions.Action0

class EmergencyKitSaveFragment: SingleFragment<EmergencyKitSavePresenter>(),
                                EmergencyKitSaveView {

    @BindView(R.id.pdf_exporter_web_view)
    override lateinit var pdfWebView: WebView

    @BindView(R.id.explanation)
    lateinit var explanationView: HtmlTextView

    @BindView(R.id.save_option_manual)
    lateinit var shareManuallyOption: MuunSaveOption

    @BindView(R.id.save_option_email)
    lateinit var sendEmailOption: MuunSaveOption

    @BindView(R.id.save_option_drive)
    lateinit var saveToDriveOption: MuunSaveOption

    /** The last export option selected by the user. */
    private var selectedOption: EmergencyKitSaveOption? = null

    /** User's choice of application the last time the share dialog was displayed. */
    private var chosenShareTarget: String? = null

    /** Whether the loading dialog is currently on screen */
    private var showingLoadingDialog = false

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_ek_save

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        saveToDriveOption.labelKind = MuunSaveOptionLabel.Kind.RECOMMENDED

        shareManuallyOption.setOnClickListener { onShareManuallyClick() }
        sendEmailOption.setOnClickListener { onSendEmailClick() }
        saveToDriveOption.setOnClickListener { onSaveToDriveClick() }

        FileSharer.onSelectionListener = { onShareApplicationChosen(it.className) }

        // Reset the selected option when creating (or re-creating) the UI:
        clearSelectedOption()

        // Set the description dynamically to add the link:
        StyledStringRes(requireContext(), R.string.ek_save_explanation, this::onWhyCloudClick)
            .toCharSequence()
            .let(explanationView::setText)
    }

    override fun setDriveUploading(isUploading: Boolean) {
        if (isUploading) {
            MuunDialog.Builder()
                .layout(R.layout.dialog_loading)
                .message(R.string.ek_uploading_body)
                .build()
                .let(this::showDialog)

            showingLoadingDialog = true

        } else if (showingLoadingDialog) {
            dismissDialog()
            showingLoadingDialog = false
        }
    }

    override fun setDriveError(error: Throwable) {
        val dialog = MuunDialog.Builder()
            .title(R.string.ek_upload_error_title)
            .message(R.string.ek_upload_error_body)
            .positiveButton(R.string.retry) { selectOption(EmergencyKitSaveOption.SAVE_TO_DRIVE) }
            .negativeButton(R.string.cancel, null)
            .build()

        showDialog(dialog)
    }

    private fun onShareManuallyClick() {
        selectOption(EmergencyKitSaveOption.SHARE_MANUALLY)
    }

    private fun onSendEmailClick() {
        selectOption(EmergencyKitSaveOption.SEND_EMAIL)
    }

    private fun onSaveToDriveClick() {
        selectOption(EmergencyKitSaveOption.SAVE_TO_DRIVE)
    }

    private fun onShareApplicationChosen(className: String?) {
        chosenShareTarget = className
    }

    override fun onResume() {
        super.onResume()

        // If we're returning from an external application, we'll automatically navigate forward.
        // We do this when returning, instead of when leaving, because not every external Intent
        // will stop this Activity. I discovered this because Google Drive has this behavior, via
        // FLAG_ACTIVITY_NEW_DOCUMENT (which I didn't manage to override).

        if (selectedOption == EmergencyKitSaveOption.SHARE_MANUALLY && chosenShareTarget != null) {
            presenter.reportManualShareStarted(chosenShareTarget)
            presenter.reportThirdPartyAppOpened()

        } else if (selectedOption == EmergencyKitSaveOption.SEND_EMAIL) {
            presenter.reportThirdPartyAppOpened()
        }

        clearSelectedOption()
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            EmergencyKitSaveOption.SAVE_TO_DRIVE.requestCode ->
                onExternalResultFromDrive(resultCode, data!!)

            EmergencyKitSaveOption.SEND_EMAIL_PICKER.requestCode ->
                onExternalResultFromEmailPicker(resultCode)

            else ->
                super.onExternalResult(requestCode, resultCode, data)
        }
    }

    override fun onEmergencyKitExported(localFile: LocalFile) {
        if (context == null || selectedOption == null) {
            return // unlikely, but maybe this Fragment was detached.
        }

        when (selectedOption) {
            EmergencyKitSaveOption.SHARE_MANUALLY -> useShareManually(localFile)
            EmergencyKitSaveOption.SEND_EMAIL -> useSendEmail()
            EmergencyKitSaveOption.SAVE_TO_DRIVE -> useSaveToDrive()
        }
    }

    override fun onBackPressed(): Boolean {
        MuunDialog.Builder()
            .title(R.string.ek_abort_title)
            .message(R.string.ek_abort_body)
            .positiveButton(R.string.abort, Action0 { presenter.goBack() })
            .negativeButton(R.string.cancel, null)
            .build()
            .let(parentActivity::showDialog)

        return true
    }

    private fun useShareManually(localFile: LocalFile) {
        // Pick the request code and file:
        val requestCode = EmergencyKitSaveOption.SHARE_MANUALLY.requestCode

        // Create a generic share Intent:
        val intent = FileSharer(requireContext()).getShareIntent(localFile.uri, localFile.type)

        requestExternalResult(requestCode, intent)
    }

    private fun useSendEmail() {
        // Get a list of email application targets:
        val fileSharer = FileSharer(requireContext())
        val targets = fileSharer.getEmailTargets()

        // If we have just one, use it:
        if (targets.size == 1) {
            shareWithEmailTarget(targets[0])
            return
        }

        // Otherwise, show an email application picker:
        val requestCode = EmergencyKitSaveOption.SEND_EMAIL_PICKER.requestCode

        val drawer = DrawerDialogFragment()
            .setTitle(R.string.save_option_email_title)

        for (target in targets) {
            drawer.addAction(target.id, target.icon, target.label)
        }

        requestExternalResult(requestCode, drawer)
    }

    private fun onExternalResultFromEmailPicker(resultCode: Int) {
        val fileSharer = FileSharer(requireContext())

        val selectedTarget = fileSharer.getEmailTargets().find { it.id == resultCode }

        if (selectedTarget != null) {
            shareWithEmailTarget(selectedTarget)
        }
    }

    private fun shareWithEmailTarget(target: FileSharer.Target) {
        // Compose the email:
        val email = presenter.composeEmergencyKitEmail()

        // Create the Intent and pick a request code:
        val intent = FileSharer(requireContext()).getEmailIntent(email, target)
        val requestCode = EmergencyKitSaveOption.SEND_EMAIL.requestCode

        presenter.reportEmailShareStarted(target.component.flattenToString())

        // Go!
        requestExternalResult(requestCode, intent)
    }

    private fun useSaveToDrive() {
        // Pick the relevant request code:
        val requestCode = EmergencyKitSaveOption.SAVE_TO_DRIVE.requestCode

        // Obtain an Intent for a Google SignIn prompt:
        val intent = presenter.getDriveAuthenticator().getSignInIntent()

        presenter.reportGoogleSignInStarted()
        requestExternalResult(requestCode, intent)
    }

    private fun onExternalResultFromDrive(resultCode: Int, data: Intent) {
        // Report back to Presenter (unless the prompt was dismissed):
        if (resultCode == Activity.RESULT_OK) {
            presenter.reportGoogleSignInComplete(data)
        } else {
            presenter.reportGoogleSignInCanceled()
        }
    }

    private fun selectOption(option: EmergencyKitSaveOption) {
        clearSelectedOption()
        selectedOption = option

        presenter.reportSelection(option)
        presenter.exportEmergencyKit()
    }

    private fun clearSelectedOption() {
        selectedOption = null
        chosenShareTarget = null
    }

    private fun onWhyCloudClick(linkId: String) {
        val drawer = TitleAndDescriptionDrawer().also {
            it.setTitle(R.string.ek_save_cloud_drawer_title)

            StyledStringRes(requireContext(), R.string.ek_save_cloud_drawer_description)
                .toCharSequence()
                .let(it::setDescription)
        }

        showDrawerDialog(drawer)
    }
}