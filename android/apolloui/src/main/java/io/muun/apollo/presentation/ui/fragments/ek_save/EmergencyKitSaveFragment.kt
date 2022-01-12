package io.muun.apollo.presentation.ui.fragments.ek_save

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.data.apis.DriveError
import io.muun.apollo.data.fs.LocalFile
import io.muun.apollo.data.os.sharer.FileSharer
import io.muun.apollo.domain.utils.applyArgs
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.postDelayed
import io.muun.apollo.presentation.ui.utils.setStyledText
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunSaveOption
import io.muun.apollo.presentation.ui.view.MuunSaveOptionLabel
import io.muun.apollo.presentation.ui.view.MuunTextInput
import rx.functions.Action0
import java.util.Locale

class EmergencyKitSaveFragment: SingleFragment<EmergencyKitSavePresenter>(),
    EmergencyKitSaveView {

    companion object {

        // List of oems that have weird rules or restrictions that make our features misbehave (e.g
        // FileSharerReceiver broadcast receiver)
        private val NAUGHTY_OEMS = setOf("huawei", "xiaomi", "oppo")

        // Window of time we use to determine a successful manual share has occurred in naughty oems
        private const val THRESHOLD_IN_SECS = 7

        private const val FEEDBACK_DIALOG_AUTO_DISMISS_MS = 2800L // developer-quality UX choice

        private const val ARG_UPDATE_KIT = "update_kit"

        @JvmStatic
        fun createForUpdate(): EmergencyKitSaveFragment =
            EmergencyKitSaveFragment().applyArgs {
                putBoolean(ARG_UPDATE_KIT, true)
            }

        @JvmStatic
        fun createForNormalExport(): EmergencyKitSaveFragment =
            EmergencyKitSaveFragment().applyArgs {
                putBoolean(ARG_UPDATE_KIT, false)
            }

        private fun isNaughtyOem(): Boolean =
            NAUGHTY_OEMS.contains(Build.MANUFACTURER.toLowerCase(Locale.getDefault()))
    }

    @BindView(R.id.pdf_exporter_web_view)
    override lateinit var pdfWebView: WebView

    @BindView(R.id.save_emergency_kit_title)
    lateinit var titleView: TextView

    @BindView(R.id.explanation)
    lateinit var explanationView: HtmlTextView

    @BindView(R.id.save_link_manual)
    lateinit var shareManuallyLink: HtmlTextView

    @BindView(R.id.save_option_drive)
    lateinit var saveToDriveOption: MuunSaveOption

    @BindView(R.id.save_option_feedback)
    lateinit var cloudFeedbackOption: MuunSaveOption

    /** The last export option selected by the user. */
    private var selectedOption: EmergencyKitSaveOption? = null

    /** User's choice of application the last time the share dialog was displayed. */
    private var chosenShareTarget: String? = null

    /** Whether the loading dialog is currently on screen */
    private var showingLoadingDialog = false

    /**
     * Extra metadata for workaround heuristic for naughty oems.
     */
    private var manuallySharedAt: Long = 0

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_ek_save

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        saveToDriveOption.labelKind = MuunSaveOptionLabel.Kind.RECOMMENDED

        saveToDriveOption.setOnClickListener { onSaveToDriveClick() }
        cloudFeedbackOption.setOnClickListener { onCloudFeedbackClick() }

        shareManuallyLink.setStyledText(R.string.save_link_manual, this::onShareManuallyClick)

        FileSharer.onSelectionListener = { onShareApplicationChosen(it.className) }

        // Reset the selected option when creating (or re-creating) the UI:
        clearSelectedOption()

        if (argumentsBundle.getBoolean(ARG_UPDATE_KIT)) {
            titleView.text = getString(R.string.tr_setup_update_kit_title)
            explanationView.text = getString(R.string.tr_setup_update_kit_desc)

        } else {
            titleView.text = getString(R.string.ek_save_title)
            explanationView.text = getString(R.string.tr_setup_update_kit_desc)
        }
    }

    override fun setDriveUploading(isUploading: Boolean) {
        if (isUploading) {
            val message = if (argumentsBundle.getBoolean(ARG_UPDATE_KIT)) {
                R.string.ek_uploading_body
            } else {
                R.string.ek_uploading_body
            }

            MuunDialog.Builder()
                .layout(R.layout.dialog_loading)
                .message(message)
                .setCancelOnTouchOutside(false)
                .build()
                .let(this::showDialog)

            showingLoadingDialog = true

        } else if (showingLoadingDialog) {
            dismissDialog()
            showingLoadingDialog = false
        }
    }

    override fun setDriveError(error: Throwable) {

        if (error is DriveError && error.isMissingPermissions()) {
            retrySaveToDrive()
            return
        }

        val dialog = MuunDialog.Builder()
            .title(R.string.ek_upload_error_title)
            .message(R.string.ek_upload_error_body)
            .positiveButton(R.string.retry, Action0 { retrySaveToDrive() })
            .negativeButton(R.string.cancel, null)
            .build()

        showDialog(dialog)
    }

    private fun onShareManuallyClick(linkId: String) {
        presenter.reportManualAdviceOpen()

        val muunDialog = MuunDialog.Builder()
            .layout(R.layout.dialog_manual_share_advice, this::initManualShareAdviceDialog)
            .build()

        muunDialog.show(viewContext)
    }

    private fun onSaveToDriveClick() {
        selectOption(EmergencyKitSaveOption.SAVE_TO_DRIVE)
    }

    private fun retrySaveToDrive() {
        onSaveToDriveClick() // just faking this is enough, no special treatment needed
    }

    private fun onCloudFeedbackClick() {
        val muunDialog = MuunDialog.Builder()
            .layout(R.layout.dialog_cloud_feedback, this::initCloudFeedbackDialog)
            .build()

        muunDialog.show(viewContext)

        presenter.reportCloudFeedbackOpen()
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

        val isManualShare = selectedOption == EmergencyKitSaveOption.SHARE_MANUALLY

        if (isManualShare && chosenShareTarget != null) {
            presenter.reportManualShareStarted(chosenShareTarget)
            presenter.reportThirdPartyAppOpened()

        } else if (isManualShare && isNaughtyOem() && secsSinceManualShare() > THRESHOLD_IN_SECS) {
            // For certain OEMs our FileSharerReceiver doesn't work properly :(. So, we came up with
            // this heuristic where we if a user using a device from one of this
            // "naughty" oems opens the share intent and spends more than THRESHOLD_IN_SECS before
            // coming back, then we consider that a successful manual share and we navigate forward
            presenter.reportManualShareStarted("naughty_oem_heuristic")
            presenter.reportThirdPartyAppOpened()
        }

        clearSelectedOption()
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            EmergencyKitSaveOption.SAVE_TO_DRIVE.requestCode ->
                onExternalResultFromDrive(resultCode, data)

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
            EmergencyKitSaveOption.SAVE_TO_DRIVE -> useSaveToDrive()
        }
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    private fun useShareManually(localFile: LocalFile) {
        // Pick the request code and file:
        val requestCode = EmergencyKitSaveOption.SHARE_MANUALLY.requestCode

        // Create a generic share Intent:
        val intent = FileSharer(requireContext()).getShareIntent(localFile.uri, localFile.type)

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

    private fun onExternalResultFromDrive(resultCode: Int, data: Intent?) {
        // Report back to Presenter (unless the prompt was dismissed):
        if (resultCode == Activity.RESULT_OK) {
            presenter.reportGoogleSignInComplete(data)
        } else {
            presenter.reportGoogleSignInCanceled()
        }
    }

    private fun initCloudFeedbackDialog(view: View, dialog: AlertDialog) {
        val formGroup = view.findViewById<ViewGroup>(R.id.dialog_form_group)
        val successGroup = view.findViewById<ViewGroup>(R.id.dialog_success_group)
        val feedbackInput = view.findViewById<MuunTextInput>(R.id.dialog_input)
        val confirmButton = view.findViewById<MuunButton>(R.id.dialog_confirm)
        val closeButton = view.findViewById<View>(R.id.dialog_close)

        feedbackInput.setOnChangeListener {
            confirmButton.isEnabled = feedbackInput.text.isNotEmpty()
        }

        feedbackInput.setText("")
        feedbackInput.requestFocusInput()

        confirmButton.setOnClickListener {
            presenter.reportCloudFeedback(feedbackInput.text.toString())

            hideKeyboard(feedbackInput)

            // Absent some animation, this is really sudden. So we set height to 0 rather than
            // visibility = GONE to at least avoid an abrupt resizing of the dialog.
            // Note: this LPs need to be FrameLayout.LayoutParams (depends on view's parent)
            formGroup.layoutParams = FrameLayout.LayoutParams(formGroup.width, 0)
            successGroup.visibility = View.VISIBLE

            // Auto-dismiss after some time (minding the fragment life-cycle):
            postDelayed(FEEDBACK_DIALOG_AUTO_DISMISS_MS) {
                if (this@EmergencyKitSaveFragment.isVisible) {
                    dialog.dismiss()
                }
            }
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun initManualShareAdviceDialog(view: View, dialog: AlertDialog) {
        val advice1 = view.findViewById<TextView>(R.id.advice_1)
        val advice2 = view.findViewById<TextView>(R.id.advice_2)
        val advice3 = view.findViewById<TextView>(R.id.advice_3)
        val confirmButton = view.findViewById<MuunButton>(R.id.dialog_confirm)
        val closeButton = view.findViewById<View>(R.id.dialog_close)

        advice1.setStyledText(R.string.ek_manual_advice_1)
        advice2.setStyledText(R.string.ek_manual_advice_2)
        advice3.setStyledText(R.string.ek_manual_advice_3)

        confirmButton.setOnClickListener {
            dialog.dismiss()
            selectOption(EmergencyKitSaveOption.SHARE_MANUALLY)
            manuallySharedAt = System.currentTimeMillis()
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
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

    private fun secsSinceManualShare(): Long =
        (System.currentTimeMillis() - manuallySharedAt) / 1000
}