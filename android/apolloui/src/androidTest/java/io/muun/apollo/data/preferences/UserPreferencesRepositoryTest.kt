package io.muun.apollo.data.preferences

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.muun.apollo.data.preferences.UserPreferencesRepository.StoredUserPreferences
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.common.model.ReceiveFormatPreference
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesRepositoryTest {

    companion object {
        const val TEST_KEY = "USER_PREFERENCES_TEST"
    }

    private lateinit var context: Context
    private lateinit var userPreferencesRepo: UserPreferencesRepository
    private var repositoryRegistry: RepositoryRegistry = RepositoryRegistry()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        userPreferencesRepo = UserPreferencesRepository(
            context,
            repositoryRegistry
        )
    }

    @After
    fun tearDown() {
        userPreferencesRepo.clear()
    }

    @Test
    fun we_can_safely_remove_user_preferences_from_Apollo() {

        // 1. Store "old" UserPreferences. Notice the presence of "lightningDefaultForReceiving"
        userPreferencesRepo.sharedPreferences
            .edit()
            .putString(
                TEST_KEY,
                """
                    {
                    "defaultAddressType":"segwit",
                    "lightningDefaultForReceiving":false,
                    "receivePreference":"UNIFIED",
                    "seenLnurlFirstTime":false,
                    "seenNewHome":true,
                    "strictMode":false
                    }
                """.trimIndent()
            ).commit()

        // 2. Check old clients can read these user preferences
        checkOldUserPreferencesRead()

        // 3. Check new clients (with lightningDefaultForReceiving removed) can read these too
        checkNewUserPreferencesRead()
    }

    private fun checkOldUserPreferencesRead() {
        val pref: Preference<OldStoredUserPreferences> = userPreferencesRepo.rxSharedPreferences
            .getObject(
                TEST_KEY,
                OldStoredUserPreferences(),
                JsonPreferenceAdapter(OldStoredUserPreferences::class.java)
            )

        val oldPrefs = pref.asObservable().toBlocking().first()

        Assertions.assertThat(oldPrefs.defaultAddressType)
            .`as`("defaultAddressType")
            .isEqualTo("segwit")

        Assertions.assertThat(oldPrefs.lightningDefaultForReceiving)
            .`as`("lightningDefaultForReceiving")
            .isEqualTo(false)

        Assertions.assertThat(oldPrefs.receivePreference)
            .`as`("receivePreference")
            .isEqualTo(ReceiveFormatPreference.UNIFIED)

        Assertions.assertThat(oldPrefs.seenLnurlFirstTime)
            .`as`("seenLnurlFirstTime")
            .isEqualTo(false)

        Assertions.assertThat(oldPrefs.seenNewHome)
            .`as`("seenNewHome")
            .isEqualTo(true)

        Assertions.assertThat(oldPrefs.strictMode)
            .`as`("strictMode")
            .isEqualTo(false)

    }

    private fun checkNewUserPreferencesRead() {
        val preference: Preference<StoredUserPreferences> = userPreferencesRepo.rxSharedPreferences
            .getObject(
                TEST_KEY,
                StoredUserPreferences(),
                JsonPreferenceAdapter(StoredUserPreferences::class.java)
            )

        val first = preference.asObservable().map { it.toModel() }.toBlocking().first()

        Assertions.assertThat(first.defaultAddressType)
            .`as`("defaultAddressType")
            .isEqualTo("segwit")

        Assertions.assertThat(first.receivePreference)
            .`as`("receivePreference")
            .isEqualTo(ReceiveFormatPreference.UNIFIED)

        Assertions.assertThat(first.seenLnurlFirstTime)
            .`as`("seenLnurlFirstTime")
            .isEqualTo(false)

        Assertions.assertThat(first.seenNewHome)
            .`as`("seenNewHome")
            .isEqualTo(true)

        Assertions.assertThat(first.strictMode)
            .`as`("strictMode")
            .isEqualTo(false)
    }

    class OldStoredUserPreferences {
        var strictMode: Boolean = false
        var seenNewHome: Boolean = false
        var seenLnurlFirstTime: Boolean = false
        var defaultAddressType: String = "segwit"
        var lightningDefaultForReceiving: Boolean = false  // We'll be removing this fellow
        var receivePreference: ReceiveFormatPreference = ReceiveFormatPreference.ONCHAIN
    }
}