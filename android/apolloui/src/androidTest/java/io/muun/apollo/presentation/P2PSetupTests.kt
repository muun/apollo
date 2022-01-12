package io.muun.apollo.presentation

import android.Manifest
import androidx.test.rule.GrantPermissionRule
import io.muun.apollo.R
import io.muun.apollo.utils.RandomUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

open class P2PSetupTests : BaseInstrumentationTest() {

    @Rule
    @JvmField
    val readContactsPermission = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS)!!

    @Test
    fun test_01_a_user_can_setup_P2P_mode() {

        grantPermission("android.permission.READ_CONTACTS")

        val user = RandomUser()

        autoFlows.signUp()
        autoFlows.setupP2P(
                phoneNumber = user.phoneNumber,
                firstName = user.firstName,
                lastName = user.lastName
        )

        homeScreen.goToSettings()

        assertThat(id(R.id.settings_username).text)
                .isEqualTo(user.fullName)

        assertThat(settingsItemContent(R.id.settings_phone_number).text)
                .isEqualTo(user.phoneNumber.toE164String())

        device.pressBack()
    }

    @Test
    fun test_02_a_user_can_add_an_existing_user_as_contact() {

        val contact = RandomUser()

        autoFlows.signUpUserWithExistingUserAsContact(contact)

        homeScreen.goToSend()

        // Expect the Muun contact to appear:
        label(contact.fullName)
                .waitForExists(15000)

        id(R.id.header)         // Check toolbar is showed, but...
        device.pressBack()      // Still use device back btn, targeting toolbar back btn is sketchy
    }
}