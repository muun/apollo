package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.utils.RandomUser
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class ChangePasswordScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    fun fillForm(user: RandomUser, newPassword: String) {

        begin()

        if (user.recoveryCode != null) {
            useRecoveryCode()
            editRecoveryCode(user.recoveryCode!!)
            confirmRecoveryCode()

        } else {
            checkNotNull(user.password)
            editOldPassword(user.password)
            confirmOldPassword()
        }

        // TODO find way to bypass email auth for remote envs like stg

        checkEmailVerificationScreenDisplayed(user.email)

        editNewPassword(newPassword)
        reEnterNewPassword(newPassword)
        acceptConditions()
        confirmNewPassword()

        finish()
    }

    private fun begin() {
        pressMuunButton(R.id.change_password_start)
    }

    private fun useRecoveryCode() {
        pressMuunButton(R.id.use_recovery_code)
    }

    private fun editRecoveryCode(recoveryCodeParts: List<String>) {
        recoveryCodeScreen.enterRecoveryCode(recoveryCodeParts)
    }

    private fun editOldPassword(oldPassword: String) {
        input(R.id.enter_old_password_input).text = oldPassword
    }

    private fun confirmRecoveryCode() {
        pressMuunButton(R.id.enter_recovery_code_continue)
    }

    private fun confirmOldPassword() {
        pressMuunButton(R.id.change_password_continue)
    }

    private fun checkEmailVerificationScreenDisplayed(email: String) {
        id(R.id.signup_waiting_for_email_verification_title).await()
        checkScreenShows(email)
    }

    private fun editNewPassword(newPassword: String) {
        input(R.id.change_password_input).text = newPassword
    }

    private fun reEnterNewPassword(newPassword: String) {
        input(R.id.change_password_confirm_input).text = newPassword
    }

    private fun confirmNewPassword() {
        pressMuunButton(R.id.change_password_continue)
    }

    private fun acceptConditions() {
        id(R.id.change_password_condition).click()
    }

    private fun finish() {
        pressMuunButton(R.id.single_action_action)
    }
}