package io.muun.apollo.presentation.ui.scan_qr;

import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.presentation.ui.base.BaseView;

public interface ScanQrView extends BaseView {

    String ARG_LNURL_FLOW = "lnurl_flow";

    void onScanError(String text);

    void onLnUrlScanError(String text);

    void setClipboardUri(OperationUri operationUri);
}
