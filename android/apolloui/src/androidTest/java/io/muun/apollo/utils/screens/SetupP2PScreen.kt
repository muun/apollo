package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.domain.model.P2PSetupStep
import io.muun.apollo.data.external.Gen
import io.muun.apollo.domain.model.user.UserPhoneNumber
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class SetupP2PScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    fun fillForm(phoneNumber: UserPhoneNumber = Gen.userPhoneNumber(),
                 verificationCode: String = Gen.numeric(6),
                 firstName: String = Gen.alpha(5),
                 lastName: String = Gen.alpha(5)) {

        editPhoneNumber(phoneNumber)
        goNext()

        editVerificationCode(verificationCode)
        goNext()

        editProfile(firstName, lastName)
        goNext()

        enableContactsPermission()
    }

    private fun editPhoneNumber(phoneNumber: UserPhoneNumber) {
        checkStep(P2PSetupStep.PHONE)

        input(R.id.signup_phone_number_edit_country_prefix).text = "+${phoneNumber.countryNumber}"
        input(R.id.signup_phone_number_edit_local_number).text = phoneNumber.nationalNumber
    }

    private fun editVerificationCode(verificationCode: String) {
        checkStep(P2PSetupStep.CONFIRM_PHONE)
        input(R.id.signup_verification_text_code).text = verificationCode
    }

    private fun editProfile(firstName: String, lastName: String) {
        checkStep(P2PSetupStep.PROFILE)

        input(R.id.signup_profile_edit_first_name).text = firstName
        input(R.id.signup_profile_edit_last_name).text = lastName
    }

    private fun enableContactsPermission() {
        checkStep(P2PSetupStep.SYNC_CONTACTS)
        pressMuunButton(R.id.sync_contacts_button)

    }

    private fun goNext() {
        pressMuunButton(R.id.signup_continue)
    }

    private fun checkStep(step: P2PSetupStep?) {
        when (step) {
            P2PSetupStep.PHONE         -> id(R.id.signup_phone_number_edit_country_prefix).await()
            P2PSetupStep.CONFIRM_PHONE -> id(R.id.signup_verification_text_code).await()
            P2PSetupStep.PROFILE       -> id(R.id.signup_profile_edit_last_name).await()
            P2PSetupStep.SYNC_CONTACTS -> id(R.id.sync_contacts_button).await()

            else ->
                throw RuntimeException("Cannot detect P2P setup step")
        }
    }

}