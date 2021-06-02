package io.muun.apollo.presentation.export

import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.print.MuunLayoutResultCallback
import android.print.MuunWriteResultCallback
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import timber.log.Timber
import java.io.File
import kotlin.random.Random


class PdfExporter(
    private val webView: WebView,
    private val html: String,
    private val destinationFile: File,
    private val callback: (PdfExportError?) -> Unit) {

    private var isUsed: Boolean = false

    private lateinit var adapter: PrintDocumentAdapter
    private lateinit var fd: ParcelFileDescriptor

    /**
     * Save a PDF render of the given HTML into the destination file. Invoke callback when done.
     */
    fun startSingleUse() {
        check(!isUsed) { "Each instance of PdfExporter can only be used once" }
        isUsed = true

        webView.webViewClient = OwnWebViewClient()

        // Set the zoom level % to default, avoiding `Accesibility > Font Size` system settings from
        // altering the PDF font size (and destroying our carefully measured layouts):
        webView.settings.textZoom = 100

        Timber.d("Loading HTML into WebView")
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun onPageLoaded() {
        Timber.d("Page loaded")

        // NOTE:
        // We've occasionally seen a rendered PDF with blank fonts. This happens at a low, but not
        // insignificant rate. We've been unable to reproduce it, but the most likely culprit is
        // this callback: according to the documentation, it "does not guarantee that the next frame
        // drawn by WebView will reflect the state of the DOM". This is the recommended remedy:
        if (WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) {
            WebViewCompat.postVisualStateCallback(
                webView,
                Random.nextLong(),
                OwnVisualStateCallback()
            )
        } else {

            // For older api levels, we resort to this.
            // TODO if in some devices this still doesn't work we'll need a workaround
            // (e.g calling intent to open html or saving html in txt file)

            // Again, attempting to eliminate the elusive bug, we'll give the event loop a shot at
            // processing this before printing to PDF. 1.5 secs delay to see if render is ready.
            Handler().postDelayed({ afterVisualStateCallback() }, 1500)
        }
    }

    private fun afterVisualStateCallback() {
        tryOrCallback {
            val jobName = "${javaClass.simpleName}-${Random.nextInt()}"

            // NOTE:
            // For reasons unknown to me, the values below must be chosen with careful precision.
            // A thousandth of an inch more or less might make the Drive PDF preview fail. While
            // other readers do work, that one is crucial for us.

            // So, they were carefully adjusted through a process of trial and error. If you change
            // them, be sure to verify that an exported kit in Google Drive can be opened.
            val dpi = 72
            val widthInMils = 5013
            val heightInMils = 14000

            val resolution = PrintAttributes.Resolution("pdf", "pdf", dpi, dpi)

            val mediaSize = PrintAttributes
                .MediaSize("MyCustomStuff", "android", widthInMils, heightInMils)

            val attributes = PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setResolution(resolution)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                webView.createPrintDocumentAdapter(jobName)

            } else {
                webView.createPrintDocumentAdapter()
            }

            Timber.d("Starting layout phase")
            adapter.onLayout(null, attributes, null, OwnLayoutCallback(), null)
        }
    }

    fun onLayoutFinished() {
        Timber.d("Layout finished successfully")

        tryOrCallback {
            // Ensure that the parent directories exist (otherwise opening the file will fail):
            destinationFile.parentFile?.mkdirs()

            // Open the file, keep the file descriptor for closing:
            fd = ParcelFileDescriptor.open(
                destinationFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            )

            // Create a CancellationSignal, required although this process is not interactive:
            val signal = CancellationSignal()

            Timber.d("Starting write phase")
            adapter.onWrite(arrayOf(PageRange.ALL_PAGES), fd, signal, OwnResultCallback())
        }
    }

    fun onLayoutFailed(message: CharSequence?) {
        Timber.d("Layout failed with error: $message")

        tryOrCallback {
            throw PdfExportError(message?.toString() ?: "Unknown layout error")
        }
    }

    fun onLayoutCancelled() {
        Timber.d("Layout cancelled")

        tryOrCallback {
            throw PdfExportError("Layout cancelled")
        }
    }

    fun onWriteFinished() {
        Timber.d("Write finished successfully")

        tryOrCallback {
            fd.close()
            callback(null) // Yes! We did it!
        }
    }

    fun onWriteFailed(message: CharSequence?) {
        Timber.d("Write failed with error: $message")

        tryOrCallback {
            fd.close()
            throw PdfExportError(message?.toString() ?: "Unknown write error")
        }
    }

    fun onWriteCancelled() {
        Timber.d("Write cancelled")

        tryOrCallback {
            fd.close()
            throw PdfExportError("Write cancelled")
        }
    }


    private inner class OwnWebViewClient: WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            this@PdfExporter.onPageLoaded()
        }

    }

    private inner class OwnLayoutCallback: MuunLayoutResultCallback() {

        override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) =
            this@PdfExporter.onLayoutFinished()

        override fun onLayoutFailed(error: CharSequence?) {
            this@PdfExporter.onLayoutFailed(error)
        }

        override fun onLayoutCancelled() {
            this@PdfExporter.onLayoutCancelled()
        }
    }

    private inner class OwnResultCallback: MuunWriteResultCallback() {

        override fun onWriteCancelled() =
            this@PdfExporter.onWriteCancelled()

        override fun onWriteFinished(pages: Array<out PageRange>?) =
            this@PdfExporter.onWriteFinished()

        override fun onWriteFailed(message: CharSequence?) =
            this@PdfExporter.onWriteFailed(message)
    }

    private inner class OwnVisualStateCallback: WebViewCompat.VisualStateCallback {
        override fun onComplete(requestId: Long) {
            // Called "when the visual state is ready to be drawn in the next WebView.onDraw",
            // according to the docs. Again, attempting to eliminate the elusive bug, we'll give the
            // event loop a shot at processing this before printing to PDF.
            Handler().postDelayed({ this@PdfExporter.afterVisualStateCallback() }, 100)
        }
    }

    /** Run a block, invoking our provided callback if an error is thrown */
    private fun tryOrCallback(f: () -> Unit) {
        try {
            f()
        } catch (e: Throwable) {
            callback(if (e is PdfExportError) e else PdfExportError(e))
        }
    }
}