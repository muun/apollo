package io.muun.apollo.domain.action.debug

import android.net.Uri
import io.muun.apollo.data.fs.FileCache
import io.muun.apollo.domain.EmailReportManager
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.report.EmailReport
import io.muun.apollo.domain.model.report.ErrorReportBuilder
import io.muun.apollo.presentation.app.Logcat
import rx.Observable
import javax.inject.Inject

class BuildDebugDataEmailReportAction @Inject constructor(
    private val fileCache: FileCache,
    private val libwalletClient: LibwalletClient,
    private val logcat: Logcat,
    private val emailReportManager: EmailReportManager,
    private val analytics: Analytics,
) : BaseAsyncAction0<EmailReport>() {

    override fun action(): Observable<EmailReport> =
        Observable.fromCallable {
            val attachmentUris = ArrayList<Uri>()

            // Attach libwallet directory zip
            attachmentUris.add(getLibwalletZipUri())

            // Attach logcat
            logcat.getLogsUri()?.let { attachmentUris.add(it) }

            // Build email report with a fake exception for metadata
            val fakeError = RuntimeException("Debug data request")
            val report = ErrorReportBuilder.build(fakeError)
            analytics.attachAnalyticsMetadata(report)

            // Building abridged email report to avoid problems with TransactionTooLargeException
            // due to adding too much data to email intent.
            emailReportManager
                .buildAbridgedEmailReport(report, "SettingsPresenter")
                .withAttachments(attachmentUris)
        }

    private fun getLibwalletZipUri(): Uri {

        // Zip libwallet directory via libwallet gRPC
        val zipFile = fileCache.getFile(FileCache.Entry.LIBWALLET_DATA)
        zipFile.parentFile?.mkdirs()
        libwalletClient.zipDataDir(zipFile.absolutePath)

        return fileCache.get(FileCache.Entry.LIBWALLET_DATA).uri
    }
}
