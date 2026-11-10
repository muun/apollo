package io.muun.apollo.presentation.ui.fragments.ek_save

import android.webkit.WebView
import io.muun.apollo.data.fs.LocalFile
import io.muun.apollo.presentation.ui.base.BaseView

interface EmergencyKitSaveView: BaseView {

    fun onEmergencyKitExported(localFile: LocalFile)

    fun setDriveUploading(isUploading: Boolean)

    fun setDriveError(error: Throwable)

    fun getPdfWebView(): WebView
}