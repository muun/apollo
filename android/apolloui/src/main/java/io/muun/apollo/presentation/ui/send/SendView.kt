package io.muun.apollo.presentation.ui.send

import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.P2PState
import io.muun.apollo.presentation.ui.base.BaseView

interface SendView : BaseView {

    fun setP2PState(state: P2PState)

    /**
     * Set whether clipboard contains plaintext content or not.
     */
    fun setClipboardStatus(containsPlainText: Boolean)

    fun setClipboardUri(uri: OperationUri?)

    fun pasteFromClipboard(clipboardContent: String)

    fun updateUriState(uriState: UriState)

}