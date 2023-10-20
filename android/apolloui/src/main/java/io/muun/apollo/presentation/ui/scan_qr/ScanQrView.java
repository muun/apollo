package io.muun.apollo.presentation.ui.scan_qr;

import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.presentation.ui.base.BaseView;

public interface ScanQrView extends BaseView {

    String ARG_LNURL_FLOW = "lnurl_flow";

    /**
     * Callback called when an error occurred during scan. Invalid scanned text is provided.
     */
    void onScanError(String text);

    /**
     * Callback called when an error occurred during an LNURL scan. Invalid scanned text is
     * provided.
     */
    void onLnUrlScanError(String text);

    /**
     * Set whether clipboard contains plaintext content or not.
     */
    void setClipboardStatus(boolean containsPlainText);

    /**
     *  Set OperationUri obtained from clipboard.
     *  Note: DEPRECATED, starting 12+ this is no longer considered good UX.
     */
    void setClipboardUri(OperationUri operationUri);

    /**
     * Display an error indicating that clipboard content is not an LNURL.
     */
    void showNoLnUrlInClipoardError();
}
