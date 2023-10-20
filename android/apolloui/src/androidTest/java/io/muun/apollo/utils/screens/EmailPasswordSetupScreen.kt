package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class EmailPasswordSetupScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    fun skipSetup() {
        pressMuunButton(R.id.create_email_skip)

        dialog.checkDisplayed(R.string.setup_password_skip_title)
        dialog.pressSkip()
    }

    fun startSetup() {
        pressMuunButton(R.id.setup_password_intro_action)
    }

    fun enterEmailAndConfirm(email: String) {
        input(R.id.create_email_input).text = email
        pressMuunButton(R.id.create_email_action)
    }

    fun checkEmailVerificationScreenDisplayed(email: String) {
        id(R.id.verify_email_title).await(10000)
        checkScreenShows(email)
    }

    /**
     * Wait for auto verify email mechanism to kick in and arrive at create password.
     * We have email auto-verification on local and regtest envs ;).
     */
    fun awaitEmailVerification(email: String, ensureEmailVerificationScreenShown: Boolean = true) {
        if (ensureEmailVerificationScreenShown) {
            checkEmailVerificationScreenDisplayed(email)
        }
        input(R.id.create_password_input).await(10000)
    }

    fun enterPasswordsAndConfirm(password: String) {
        input(R.id.create_password_input).text = password
        input(R.id.create_password_confirm_input).text = password

        pressMuunButton(R.id.create_password_confirm)
    }

    fun acceptConditions() {
        id(R.id.setup_password_accept_condition_1).click()
        id(R.id.setup_password_accept_condition_2).click()

        pressMuunButton(R.id.setup_password_accept_action)
    }

    fun finishSetup() {
        pressMuunButton(R.id.setup_password_success_action)
    }

    fun tryClose() {
        toolbar.pressClose()
        checkAbortDialogDisplayed()
        dismissAbortDialog()
    }

    fun abortFlow() {
        toolbar.pressClose()
        checkAbortDialogDisplayed()
        confirmAbort()
    }

    private fun checkAbortDialogDisplayed() {
        dialog.checkDisplayed(R.string.setup_password_abort_body)
    }

    private fun dismissAbortDialog() {
        dialog.pressCancel()
    }

    private fun confirmAbort() {
        dialog.pressAbort()
    }
}