package io.muun.apollo.presentation.ui.scan_qr;

import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.new_operation.NewOperationOrigin;

import javax.inject.Inject;

@PerActivity
public class ScanQrPresenter extends BasePresenter<ScanQrView> {

    /**
     * Constructor.
     */
    @Inject
    public ScanQrPresenter() {
    }

    /**
     * Detect whether a scanned QR code contains a bitcoin payment request.
     */
    public void newOperationFromScannedText(String text) {
        final OperationUri uri;
        final NewOperationOrigin origin = NewOperationOrigin.SCAN_QR;

        try {
            uri = OperationUri.fromString(text);

        } catch (IllegalArgumentException ex) {
            view.onScanError(text);
            analytics.report(new AnalyticsEvent.S_NEW_OP_ERROR(
                    AnalyticsEvent.S_NEW_OP_ORIGIN.fromModel(origin),
                    AnalyticsEvent.S_NEW_OP_ERROR_TYPE.INVALID_ADDRESS,
                    text
            ));

            return;
        }

        navigator.navigateToNewOperation(getContext(), origin, uri);
        view.finishActivity();
    }

    public void reportCameraPermissionAsked() {
        analytics.report(new AnalyticsEvent.E_SCAN_QR_ASK_CAMERA_PERMISSION());
    }

    public void reportCameraPermissionGranted() {
        analytics.report(new AnalyticsEvent.E_SCAN_QR_CAMERA_PERMISSION_GRANTED());
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_SCAN_QR();
    }
}
