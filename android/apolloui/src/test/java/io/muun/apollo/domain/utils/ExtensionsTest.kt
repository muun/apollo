package io.muun.apollo.domain.utils

import io.muun.apollo.BaseTest
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.os.secure_storage.debugSnapshot
import io.muun.apollo.data.os.secure_storage.secureStorageError
import io.muun.apollo.domain.errors.ChallengeKeyMigrationError
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.errors.WeirdIncorrectAttemptsBugError
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ExtensionsTest : BaseTest() {

    // What counts as corruption decides whether the user is sent to restore the wallet from
    // its recovery code, so each case in the taxonomy is pinned here rather than left to the
    // predicate's shape. Both storage APIs are in use at once, so the same condition has to
    // get the same answer whichever one the call site went through.

    @Test
    fun `should report corruption for a decryption failure`() {
        val error = SecureKeyValueStoreError.DecryptionFailed(secureStorageError())

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isTrue()
    }

    @Test
    fun `should report corruption for an unusable store`() {
        val error = SecureKeyValueStoreError.StorageFailed(secureStorageError())

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isTrue()
    }

    @Test
    fun `should report corruption for a legacy secure storage error`() {
        // Nothing routed it through securekv, so the legacy scan is what answers.
        assertThat(secureStorageError().isInstanceOrIsCausedBySecureStorageCorruption()).isTrue()
    }

    @Test
    fun `should report corruption for a corrupt keystore under a legacy error`() {
        // The legacy subclasses that do report a finding about the data stay corruption.
        val error = SecureStorageProvider.KeyStoreCorruptedError("key", debugSnapshot())

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isTrue()
    }

    @Test
    fun `should report corruption for a wrapped legacy secure storage error`() {
        // Widens what the presenter used to match with an instanceof check. Rx delivers errors
        // untouched; what wraps them is our own code, and MigrateChallengeKeysAction replaces
        // every throwable with a ChallengeKeyMigrationError before the presenter sees it.
        val error = ChallengeKeyMigrationError(secureStorageError())

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isTrue()
    }

    @Test
    fun `should not report corruption for a missing key`() {
        // Nothing is known to be wrong with the data: the key was simply never stored.
        val error = SecureKeyValueStoreError.NotFound()

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isFalse()
    }

    @Test
    fun `should not report corruption for a missing legacy key`() {
        // The same condition as NotFound, reached through the legacy API. Answering differently
        // would make the classification depend on which storage call the caller happened to use.
        val error = SecureStorageProvider.SecureStorageNoSuchElementError("key", debugSnapshot())

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isFalse()
    }

    @Test
    fun `should not report corruption for an out of range attempt counter`() {
        // Raised before the pin is ever read, and only a SecureStorageError so that it can
        // carry a debug snapshot. Calling it corruption would offer a logout over a counter.
        val error = WeirdIncorrectAttemptsBugError(-1, 3, debugSnapshot())

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isFalse()
    }

    @Test
    fun `should not report corruption for a wrapped missing key`() {
        // The shape the challenge key migration produces: seasonPublicChallengeKey reads a key
        // the user may never have had, and the action wraps whatever comes out of it.
        val error = ChallengeKeyMigrationError(SecureKeyValueStoreError.NotFound())

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isFalse()
    }

    @Test
    fun `should not report corruption for a wrapped missing legacy key`() {
        val error = ChallengeKeyMigrationError(
            SecureStorageProvider.SecureStorageNoSuchElementError("key", debugSnapshot())
        )

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isFalse()
    }

    @Test
    fun `should not report corruption for a failed keystore call`() {
        // The shape delete(), wipe() and the presence probes produce: the call never reached
        // the stored data, so nothing was learned about it.
        val error = SecureKeyValueStoreError.OperationFailed(RuntimeException("boom"))

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isFalse()
    }

    @Test
    fun `should prefer the securekv classification over the legacy cause`() {
        // Not a shape production builds today, but the rule it pins is what keeps the securekv
        // cases from being re-read as corruption by the legacy scan underneath them.
        val error = SecureKeyValueStoreError.OperationFailed(secureStorageError())

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isFalse()
    }

    @Test
    fun `should find corruption through the cause chain`() {
        val error = ChallengeKeyMigrationError(
            SecureKeyValueStoreError.StorageFailed(secureStorageError())
        )

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isTrue()
    }

    @Test
    fun `should not report corruption for an unrelated error`() {
        val error = RuntimeException("boom")

        assertThat(error.isInstanceOrIsCausedBySecureStorageCorruption()).isFalse()
    }

    // The lock screen decides between stranding the user behind a PIN that can never work and
    // showing them a way out, so each answer is pinned rather than left to the predicate's shape.

    @Test
    fun `should report an unreadable value for a decryption failure`() {
        val error = SecureKeyValueStoreError.DecryptionFailed(secureStorageError())

        assertThat(error.isUnreadableSecureKeyValueStoreValue()).isTrue()
    }

    @Test
    fun `should report an unreadable value for an unusable store`() {
        val error = SecureKeyValueStoreError.StorageFailed(secureStorageError())

        assertThat(error.isUnreadableSecureKeyValueStoreValue()).isTrue()
    }

    @Test
    fun `should report an unreadable value for a failed keystore call`() {
        // Not corruption, but the read still came back empty-handed, and the caller's question
        // is whether it got the value, not whether the store is broken.
        val error = SecureKeyValueStoreError.OperationFailed(RuntimeException("boom"))

        assertThat(error.isUnreadableSecureKeyValueStoreValue()).isTrue()
    }

    @Test
    fun `should not report an unreadable value for a missing key`() {
        // The opposite finding: nothing is stored, so there is no value to fail to read.
        val error = SecureKeyValueStoreError.NotFound()

        assertThat(error.isUnreadableSecureKeyValueStoreValue()).isFalse()
    }

    @Test
    fun `should not report an unreadable value for a legacy secure storage error`() {
        // The incorrect attempt counter still goes through the legacy API, and its failures are
        // not a finding about the value this asks about.
        assertThat(secureStorageError().isUnreadableSecureKeyValueStoreValue()).isFalse()
    }

    @Test
    fun `should find an unreadable value through the cause chain`() {
        val error = ChallengeKeyMigrationError(
            SecureKeyValueStoreError.DecryptionFailed(secureStorageError())
        )

        assertThat(error.isUnreadableSecureKeyValueStoreValue()).isTrue()
    }

    // This will feed the decision to wipe a wallet copied without its keystore keys, so each
    // answer is pinned here rather than left to the predicate's shape.

    @Test
    fun `should report a missing keystore key for its securekv type`() {
        // GIVEN
        val error = SecureKeyValueStoreError.KeystoreKeyMissing(
            SecureStorageProvider.KeyStoreCorruptedError("key", debugSnapshot())
        )

        // WHEN
        val actual = error.isInstanceOrIsCausedByMissingKeystoreKey()

        // THEN
        assertThat(actual).isTrue()
    }

    @Test
    fun `should report a missing keystore key for its legacy type`() {
        // GIVEN
        val error = SecureStorageProvider.KeyStoreCorruptedError("key", debugSnapshot())

        // WHEN
        val actual = error.isInstanceOrIsCausedByMissingKeystoreKey()

        // THEN
        assertThat(actual).isTrue()
    }

    @Test
    fun `should find a missing keystore key through the cause chain`() {
        // GIVEN
        val error = ChallengeKeyMigrationError(
            SecureStorageProvider.KeyStoreCorruptedError("key", debugSnapshot())
        )

        // WHEN
        val actual = error.isInstanceOrIsCausedByMissingKeystoreKey()

        // THEN
        assertThat(actual).isTrue()
    }

    @Test
    fun `should not report a missing keystore key for other securekv failures`() {
        // GIVEN
        val errors = listOf(
            SecureKeyValueStoreError.DecryptionFailed(secureStorageError()),
            SecureKeyValueStoreError.StorageFailed(secureStorageError()),
            SecureKeyValueStoreError.NotFound(),
        )

        // WHEN
        val reported = errors.filter { it.isInstanceOrIsCausedByMissingKeystoreKey() }

        // THEN
        assertThat(reported).isEmpty()
    }

    @Test
    fun `should prefer the securekv classification over a legacy missing keystore key`() {
        // GIVEN
        // Not a shape production builds today, but it pins the rule that a legacy cause alone
        // would qualify and the securekv type around it wins.
        val error = SecureKeyValueStoreError.OperationFailed(
            SecureStorageProvider.KeyStoreCorruptedError("key", debugSnapshot())
        )

        // WHEN
        val actual = error.isInstanceOrIsCausedByMissingKeystoreKey()

        // THEN
        assertThat(actual).isFalse()
    }

    @Test
    fun `should not report a missing keystore key for other legacy failures`() {
        // GIVEN
        val errors = listOf(
            secureStorageError(),
            SecureStorageProvider.SharedPreferencesCorruptedError("key", debugSnapshot()),
            SecureStorageProvider.SecureStorageNoSuchElementError("key", debugSnapshot()),
        )

        // WHEN
        val reported = errors.filter { it.isInstanceOrIsCausedByMissingKeystoreKey() }

        // THEN
        assertThat(reported).isEmpty()
    }

    @Test
    fun `should not report a missing keystore key for an unrelated error`() {
        // GIVEN
        val error = RuntimeException("boom")

        // WHEN
        val actual = error.isInstanceOrIsCausedByMissingKeystoreKey()

        // THEN
        assertThat(actual).isFalse()
    }
}
