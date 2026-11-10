package io.muun.apollo.data.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.afs.MetricsProvider
import io.muun.apollo.data.preferences.FirstInstallTimeRepository.Companion.FIRST_INSTALL_TIME_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FirstInstallTimeRepositoryTest : BaseTest() {

    private companion object {
        const val AN_INSTALL_TIME = 1_700_000_000_000L
        const val A_LATER_INSTALL_TIME = 1_800_000_000_000L
        const val AN_UNREPORTED_INSTALL_TIME = 0L
    }

    private val sharedPreferences = FakeSharedPreferences()

    private val context: Context = mockk {
        every { getSharedPreferences(any(), any()) } returns sharedPreferences
    }

    @Test
    fun `should record the install time when nothing was recorded yet`() {
        // GIVEN
        val repository = repositoryReporting(AN_INSTALL_TIME)

        // WHEN
        repository.recordCurrentInstallTimeIfAbsent()

        // THEN
        assertThat(sharedPreferences.getLong(FIRST_INSTALL_TIME_KEY, 0)).isEqualTo(AN_INSTALL_TIME)
    }

    @Test
    fun `should keep what was recorded when the current install time differs`() {
        // GIVEN the shape a device-to-device copy leaves behind: the preferences arrive from the
        // old install, and overwriting here would erase the only evidence that they did.
        repositoryReporting(AN_INSTALL_TIME).recordCurrentInstallTimeIfAbsent()
        val repository = repositoryReporting(A_LATER_INSTALL_TIME)

        // WHEN
        repository.recordCurrentInstallTimeIfAbsent()

        // THEN
        assertThat(sharedPreferences.getLong(FIRST_INSTALL_TIME_KEY, 0)).isEqualTo(AN_INSTALL_TIME)
    }

    @Test
    fun `should record nothing when reading the install time fails`() {
        // GIVEN a provider that throws: a failed read is never recorded.
        val metricsProvider: MetricsProvider = mockk {
            every { firstInstallTimeInMs } throws RuntimeException("boom")
        }
        val repository = FirstInstallTimeRepository(context, RepositoryRegistry(), metricsProvider)

        // WHEN
        repository.recordCurrentInstallTimeIfAbsent()

        // THEN
        assertThat(sharedPreferences.contains(FIRST_INSTALL_TIME_KEY)).isFalse()
    }

    @Test
    fun `should record nothing when the device reports no install time`() {
        // GIVEN
        val repository = repositoryReporting(AN_UNREPORTED_INSTALL_TIME)

        // WHEN
        repository.recordCurrentInstallTimeIfAbsent()

        // THEN
        assertThat(sharedPreferences.contains(FIRST_INSTALL_TIME_KEY)).isFalse()
    }

    @Test
    fun `should replace an unknown install time an earlier version recorded`() {
        // GIVEN
        sharedPreferences.edit()
            .putLong(FIRST_INSTALL_TIME_KEY, AN_UNREPORTED_INSTALL_TIME)
            .commit()
        val repository = repositoryReporting(AN_INSTALL_TIME)

        // WHEN
        repository.recordCurrentInstallTimeIfAbsent()

        // THEN
        assertThat(sharedPreferences.getLong(FIRST_INSTALL_TIME_KEY, 0)).isEqualTo(AN_INSTALL_TIME)
    }

    @Test
    fun `should record nothing when the device reports an install time before the epoch`() {
        // GIVEN
        val repository = repositoryReporting(-1)

        // WHEN
        repository.recordCurrentInstallTimeIfAbsent()

        // THEN
        assertThat(sharedPreferences.contains(FIRST_INSTALL_TIME_KEY)).isFalse()
    }

    @Test
    fun `should replace what was recorded with the current install time on rebase`() {
        // GIVEN
        repositoryReporting(AN_INSTALL_TIME).recordCurrentInstallTimeIfAbsent()
        val repository = repositoryReporting(A_LATER_INSTALL_TIME)

        // WHEN
        repository.rebaseToCurrentInstall()

        // THEN
        assertThat(sharedPreferences.getLong(FIRST_INSTALL_TIME_KEY, 0))
            .isEqualTo(A_LATER_INSTALL_TIME)
    }

    @Test
    fun `should record again on the launch after a rebase dropped the value`() {
        // GIVEN
        repositoryReporting(AN_INSTALL_TIME).recordCurrentInstallTimeIfAbsent()
        repositoryReporting(AN_UNREPORTED_INSTALL_TIME).rebaseToCurrentInstall()
        val repository = repositoryReporting(A_LATER_INSTALL_TIME)

        // WHEN
        repository.recordCurrentInstallTimeIfAbsent()

        // THEN
        assertThat(sharedPreferences.getLong(FIRST_INSTALL_TIME_KEY, 0))
            .isEqualTo(A_LATER_INSTALL_TIME)
    }

    @Test
    fun `should expose the recorded and the current install time`() {
        // GIVEN
        repositoryReporting(AN_INSTALL_TIME).recordCurrentInstallTimeIfAbsent()
        val repository = repositoryReporting(A_LATER_INSTALL_TIME)

        // WHEN
        val actualRecorded = repository.recordedInstallTimeInMs()
        val actualCurrent = repository.currentInstallTimeInMs()

        // THEN
        assertThat(actualRecorded).isEqualTo(AN_INSTALL_TIME)
        assertThat(actualCurrent).isEqualTo(A_LATER_INSTALL_TIME)
    }

    @Test
    fun `should expose no install time when there's none recorded or it can't be read`() {
        // GIVEN
        val repository = repositoryReporting(AN_UNREPORTED_INSTALL_TIME)

        // WHEN
        val actualRecorded = repository.recordedInstallTimeInMs()
        val actualCurrent = repository.currentInstallTimeInMs()

        // THEN
        assertThat(actualRecorded).isNull()
        assertThat(actualCurrent).isNull()
    }

    @Test
    fun `should expose no recorded install time when an earlier version recorded none`() {
        for (recorded in listOf(0L, -1L)) {
            // GIVEN what earlier versions recorded when PackageManager reported no install time.
            sharedPreferences.edit().putLong(FIRST_INSTALL_TIME_KEY, recorded).commit()
            val repository = repositoryReporting(AN_INSTALL_TIME)

            // WHEN
            val actualRecorded = repository.recordedInstallTimeInMs()

            // THEN
            assertThat(actualRecorded).describedAs("recorded $recorded").isNull()
        }
    }

    private fun repositoryReporting(installTime: Long): FirstInstallTimeRepository {
        val metricsProvider: MetricsProvider = mockk {
            every { firstInstallTimeInMs } returns installTime
        }

        return FirstInstallTimeRepository(context, RepositoryRegistry(), metricsProvider)
    }
}
