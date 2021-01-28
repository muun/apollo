package io.muun.apollo.domain

import io.muun.apollo.data.os.ClipboardProvider
import javax.inject.Inject

class ClipboardManager @Inject constructor(private val clipboardProvider: ClipboardProvider) {

    fun copy(label: String, text: String) =
        clipboardProvider.copy(label, text)
}