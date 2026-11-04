package io.muun.apollo.data.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.afs.Constants
import io.muun.apollo.data.afs.MetricsProvider
import io.muun.apollo.data.preferences.AndroidIdRepository.Companion.ANDROID_ID_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AndroidIdRepositoryTest : BaseTest() {

    private companion object {
        const val AN_ANDROID_ID = "3f2a9c1e7b4d6a05"
        const val ANOTHER_ANDROID_ID = "1b4e28ba2fa1aa2b"
    }

    private val sharedPreferences = FakeSharedPreferences()

    private val context: Context = mockk {
        every { getSharedPreferences(any(), any()) } returns sharedPreferences
    }

    @Test
    fun `should record the ANDROID_ID when nothing was recorded yet`() {
        // GIVEN
        val repository = repositoryReporting(AN_ANDROID_ID)

        // WHEN
        repository.recordCurrentAndroidIdIfAbsent()

        // THEN
        assertThat(sharedPreferences.getString(ANDROID_ID_KEY, null)).isEqualTo(AN_ANDROID_ID)
    }

    @Test
    fun `should keep what was recorded when the current ANDROID_ID differs`() {
        // GIVEN the shape a device-to-device copy leaves behind.
        repositoryReporting(AN_ANDROID_ID).recordCurrentAndroidIdIfAbsent()
        val repository = repositoryReporting(ANOTHER_ANDROID_ID)

        // WHEN
        repository.recordCurrentAndroidIdIfAbsent()

        // THEN
        assertThat(sharedPreferences.getString(ANDROID_ID_KEY, null)).isEqualTo(AN_ANDROID_ID)
    }

    @Test
    fun `should record nothing when the device reports an unknown ANDROID_ID`() {
        // GIVEN
        val repository = repositoryReporting(Constants.UNKNOWN)

        // WHEN
        repository.recordCurrentAndroidIdIfAbsent()

        // THEN
        assertThat(sharedPreferences.contains(ANDROID_ID_KEY)).isFalse()
    }

    @Test
    fun `should record nothing when the device reports a blank ANDROID_ID`() {
        // GIVEN
        val repository = repositoryReporting(" ")

        // WHEN
        repository.recordCurrentAndroidIdIfAbsent()

        // THEN
        assertThat(sharedPreferences.contains(ANDROID_ID_KEY)).isFalse()
    }

    @Test
    fun `should record nothing when reading the ANDROID_ID fails`() {
        // GIVEN a provider that throws: a failed read is never recorded.
        val metricsProvider: MetricsProvider = mockk {
            every { androidId } throws RuntimeException("boom")
        }
        val repository = AndroidIdRepository(context, RepositoryRegistry(), metricsProvider)

        // WHEN
        repository.recordCurrentAndroidIdIfAbsent()

        // THEN
        assertThat(sharedPreferences.contains(ANDROID_ID_KEY)).isFalse()
    }

    @Test
    fun `should survive logout`() {
        // GIVEN
        val registry = RepositoryRegistry()
        val metricsProvider: MetricsProvider = mockk()

        // WHEN
        val repository = AndroidIdRepository(context, registry, metricsProvider)

        // THEN
        assertThat(registry.repositoriesToClearOnLogout()).doesNotContain(repository)
    }

    @Test
    fun `should replace what was recorded with the current ANDROID_ID on rebase`() {
        // GIVEN
        repositoryReporting(AN_ANDROID_ID).recordCurrentAndroidIdIfAbsent()
        val repository = repositoryReporting(ANOTHER_ANDROID_ID)

        // WHEN
        repository.rebaseToCurrentInstall()

        // THEN
        assertThat(sharedPreferences.getString(ANDROID_ID_KEY, null)).isEqualTo(ANOTHER_ANDROID_ID)
    }

    @Test
    fun `should record again on the launch after a rebase dropped the value`() {
        // GIVEN
        repositoryReporting(AN_ANDROID_ID).recordCurrentAndroidIdIfAbsent()
        repositoryReporting(Constants.UNKNOWN).rebaseToCurrentInstall()
        val repository = repositoryReporting(ANOTHER_ANDROID_ID)

        // WHEN
        repository.recordCurrentAndroidIdIfAbsent()

        // THEN
        assertThat(sharedPreferences.getString(ANDROID_ID_KEY, null)).isEqualTo(ANOTHER_ANDROID_ID)
    }

    @Test
    fun `should expose the recorded and the current ANDROID_ID`() {
        // GIVEN
        repositoryReporting(AN_ANDROID_ID).recordCurrentAndroidIdIfAbsent()
        val repository = repositoryReporting(ANOTHER_ANDROID_ID)

        // WHEN
        val actualRecorded = repository.recordedAndroidId()
        val actualCurrent = repository.currentAndroidId()

        // THEN
        assertThat(actualRecorded).isEqualTo(AN_ANDROID_ID)
        assertThat(actualCurrent).isEqualTo(ANOTHER_ANDROID_ID)
    }

    @Test
    fun `should expose no ANDROID_ID when there's none recorded or it can't be read`() {
        // GIVEN
        val repository = repositoryReporting(Constants.UNKNOWN)

        // WHEN
        val actualRecorded = repository.recordedAndroidId()
        val actualCurrent = repository.currentAndroidId()

        // THEN
        assertThat(actualRecorded).isNull()
        assertThat(actualCurrent).isNull()
    }

    @Test
    fun `should expose no recorded ANDROID_ID when the preference holds an unknown one`() {
        for (recorded in listOf(Constants.UNKNOWN, "")) {
            // GIVEN a value it never records, should the preference hold one anyway.
            sharedPreferences.edit().putString(ANDROID_ID_KEY, recorded).commit()
            val repository = repositoryReporting(AN_ANDROID_ID)

            // WHEN
            val actualRecorded = repository.recordedAndroidId()

            // THEN
            assertThat(actualRecorded).describedAs("recorded '$recorded'").isNull()
        }
    }

    private fun repositoryReporting(reportedAndroidId: String): AndroidIdRepository {
        val metricsProvider: MetricsProvider = mockk {
            every { androidId } returns reportedAndroidId
        }

        return AndroidIdRepository(context, RepositoryRegistry(), metricsProvider)
    }
}
