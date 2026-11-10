package io.muun.apollo.domain

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.spyk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.secure_storage.FakeKeyStore
import io.muun.apollo.data.os.secure_storage.FakePreferences
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreTestWiring
import io.muun.apollo.domain.model.SignupDraft
import io.muun.apollo.domain.model.SignupStep
import io.muun.common.utils.Encodings
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignupDraftManagerTest : BaseTest() {

    companion object {
        // Mirrors the private constant in SignupDraftManager for test setup
        private const val SIGNUP_DRAFT_KEY = "signup_draft_key"
    }

    @MockK(relaxed = true)
    lateinit var context: Context

    private lateinit var secureStorageProvider: SecureStorageProvider

    private lateinit var signupDraftManager: SignupDraftManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        secureStorageProvider = SecureStorageProvider(
            FakeKeyStore(),
            FakePreferences(),
        )

        signupDraftManager = SignupDraftManager(
            context,
            SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider),
        )
    }

    @Test
    fun `should return null when no draft is stored`() {
        // Then
        assertNull(signupDraftManager.fetchSignupDraft())
    }

    @Test
    fun `should return the saved draft`() {
        // Given
        val expected = SignupDraft(step = SignupStep.LOGIN_EMAIL, email = "user@muun.com")
        signupDraftManager.save(expected)

        // When
        val actual = signupDraftManager.fetchSignupDraft()

        // Then
        assertEquals(expected.step, actual?.step)
        assertEquals(expected.email, actual?.email)
    }

    @Test
    fun `should return null when the stored draft cannot be parsed`() {
        // Given
        secureStorageProvider.put(SIGNUP_DRAFT_KEY, Encodings.stringToBytes("not a draft"))

        // When
        val actual = signupDraftManager.fetchSignupDraft()

        // Then
        assertNull(actual)
        // Nothing is discarded: the unparseable record is still in storage afterwards.
        assertTrue(secureStorageProvider.has(SIGNUP_DRAFT_KEY))
    }

    @Test
    fun `should wipe the draft bytes after storing them`() {
        // Given
        val repository = spyk(SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider))
        val handed = slot<ByteArray>()
        var handedContent = ""
        every { repository.put(SIGNUP_DRAFT_KEY, capture(handed)) } answers {
            handedContent = String(handed.captured)
            callOriginal()
        }
        val draft = SignupDraft(step = SignupStep.LOGIN_EMAIL, email = "user@muun.com")

        // When
        SignupDraftManager(context, repository).save(draft)

        // Then
        assertTrue(handedContent.contains("user@muun.com"))
        assertTrue(handed.captured.all { it == 0.toByte() })
    }

    @Test
    fun `should wipe the draft bytes when storing them fails`() {
        // Given
        val repository = spyk(SecureKeyValueStoreTestWiring.repositoryOver(secureStorageProvider))
        val handed = slot<ByteArray>()
        var handedContent = ""
        every { repository.put(SIGNUP_DRAFT_KEY, capture(handed)) } answers {
            handedContent = String(handed.captured)
            throw IllegalStateException("storage unavailable")
        }
        val draft = SignupDraft(step = SignupStep.LOGIN_EMAIL, email = "user@muun.com")

        // When / Then
        assertThatThrownBy { SignupDraftManager(context, repository).save(draft) }
            .isInstanceOf(IllegalStateException::class.java)
        assertTrue(handedContent.contains("user@muun.com"))
        assertTrue(handed.captured.all { it == 0.toByte() })
    }

    @Test
    fun `should return null after clearing the draft`() {
        // Given
        signupDraftManager.save(SignupDraft())

        // When
        signupDraftManager.clear()

        // Then
        assertNull(signupDraftManager.fetchSignupDraft())
    }
}
