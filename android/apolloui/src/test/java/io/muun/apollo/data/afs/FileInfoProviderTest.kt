package io.muun.apollo.data.afs

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.muun.apollo.data.os.TorHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit


class FileInfoProviderTest {
    private lateinit var provider: FileInfoProvider

    private val DEFAULT_DATE = LocalDate.now(ZoneOffset.UTC)
        .minusYears(1)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    private fun fileWithDate(epoch: Long): File =
        mockk<File> {
            every { lastModified() } returns epoch
        }

    private fun fileWithName(fileName: String): File =
        mockk<File> {
            every { isFile } returns true
            every { isDirectory } returns false
            every { this@mockk.name } returns fileName
        }

    private fun dirWithName(dirName: String): File =
        mockk<File> {
            every { isFile } returns false
            every { isDirectory } returns true
            every { this@mockk.name } returns dirName
        }

    @Before
    fun setup() {
        val context = mockk<Context>(relaxed = true)
        provider = spyk(FileInfoProvider(context))
    }

    @Test
    fun externalMinDateWithNonPermissions() {
        every { provider.getFilesFromDir(any()) } returns null

        assertEquals(Constants.LONG_UNKNOWN, provider.externalMinDate)
    }

    @Test
    fun externalMinDateWithDefaultFiles() {
        val files = arrayOf(
            fileWithDate(DEFAULT_DATE),
            fileWithDate(DEFAULT_DATE)
        )

        every { provider.getFilesFromDir(any()) } returns files
        every { provider.defaultDate } returns DEFAULT_DATE

        assertEquals(DEFAULT_DATE, provider.externalMinDate)
    }

    @Test
    fun externalMinDateWithDefaultAndNewDateFiles() {
        val newer = Instant.ofEpochMilli(DEFAULT_DATE)
            .plus(2, ChronoUnit.DAYS)
            .toEpochMilli()

        val files = arrayOf(
            fileWithDate(newer),
            fileWithDate(DEFAULT_DATE)
        )

        every { provider.getFilesFromDir(any()) } returns files
        every { provider.defaultDate } returns DEFAULT_DATE

        assertEquals(newer, provider.externalMinDate)
    }

    @Test
    fun externalMinDateWithDefaultAndOldDateFiles() {
        val older = Instant.ofEpochMilli(DEFAULT_DATE)
            .minus(2, ChronoUnit.DAYS)
            .toEpochMilli()

        val files = arrayOf(
            fileWithDate(older),
            fileWithDate(DEFAULT_DATE)
        )

        every { provider.getFilesFromDir(any()) } returns files
        every { provider.defaultDate } returns DEFAULT_DATE

        assertEquals(older, provider.externalMinDate)
    }

    @Test
    fun externalMinDateWithDifferentModFiles() {
        val older = Instant.ofEpochMilli(DEFAULT_DATE)
            .minus(2, ChronoUnit.DAYS)
            .toEpochMilli()

        val newer = Instant.ofEpochMilli(DEFAULT_DATE)
            .plus(2, ChronoUnit.DAYS)
            .toEpochMilli()

        val files = arrayOf(
            fileWithDate(newer),
            fileWithDate(older),
            fileWithDate(DEFAULT_DATE)
        )

        every { provider.getFilesFromDir(any()) } returns files
        every { provider.defaultDate } returns DEFAULT_DATE

        assertEquals(older, provider.externalMinDate)
    }

    @Test
    fun appExternalNewEntriesWithNonPermissions() {
        every { provider.getFilesFromDir(any()) } returns null

        assertEquals(Constants.INT_UNKNOWN, provider.hasNewEntriesInAppExternalStorage)
    }

    @Test
    fun appExternalNewEntriesWithEmptyFiles() {
        val files = emptyArray<File>()

        every { provider.getFilesFromDir(any()) } returns files

        assertEquals(Constants.INT_ABSENT, provider.hasNewEntriesInAppExternalStorage)
    }

    @Test
    fun appExternalNewEntriesWithUnexpectedFile() {
        every { provider.getFilesFromDir(any()) } returns arrayOf(
            fileWithName("unexpected.txt")
        )

        assertEquals(Constants.INT_PRESENT, provider.hasNewEntriesInAppExternalStorage)
    }

    @Test
    fun appExternalNewEntriesWithUnexpectedDir() {
        every { provider.getFilesFromDir(any()) } returns arrayOf(
            dirWithName("unexpected")
        )

        assertEquals(Constants.INT_PRESENT, provider.hasNewEntriesInAppExternalStorage)
    }

    @Test
    fun appExternalNewEntriesWithExpectedDir() {
        every { provider.getFilesFromDir(any()) } returns arrayOf(
            dirWithName(TorHelper.process(provider.APP_EXTERNAL_DIRS.first()))
        )

        assertEquals(Constants.INT_ABSENT, provider.hasNewEntriesInAppExternalStorage)
    }

    @Test
    fun appExternalNewEntriesWithAllExpectedDirs() {
        every { provider.getFilesFromDir(any()) } returns
            provider.APP_EXTERNAL_DIRS
                .map { dirWithName(TorHelper.process(it)) }
                .toTypedArray()

        assertEquals(Constants.INT_ABSENT, provider.hasNewEntriesInAppExternalStorage)
    }

    @Test
    fun appExternalNewEntriesWithExpectedAndUnexpectedDirs() {
        every { provider.getFilesFromDir(any()) } returns
            provider.APP_EXTERNAL_DIRS
                .map { dirWithName(TorHelper.process(it)) }
                .toTypedArray()
                .plus(dirWithName("unexpected"))

        assertEquals(Constants.INT_PRESENT, provider.hasNewEntriesInAppExternalStorage)
    }

    @Test
    fun appExternalNewEntriesWithExpectedDirsAndUnexpectedFile() {
        every { provider.getFilesFromDir(any()) } returns
            provider.APP_EXTERNAL_DIRS
                .map { dirWithName(TorHelper.process(it)) }
                .toTypedArray()
                .plus(fileWithName("unexpected.txt"))

        assertEquals(Constants.INT_PRESENT, provider.hasNewEntriesInAppExternalStorage)
    }
}