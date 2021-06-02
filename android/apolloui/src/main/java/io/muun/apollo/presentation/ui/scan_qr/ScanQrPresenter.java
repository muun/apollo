package io.muun.apollo.presentation.ui.scan_qr;

import io.muun.apollo.domain.libwallet.LnUrl;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.selector.ClipboardUriSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.new_operation.NewOperationOrigin;

import android.os.Bundle;
import icepick.State;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import rx.Observable;

import javax.inject.Inject;

import static io.muun.apollo.presentation.ui.scan_qr.ScanQrView.ARG_LNURL_FLOW;

@PerActivity
public class ScanQrPresenter extends BasePresenter<ScanQrView> {

    @State
    LnUrlFlow lnUrlFlow;

    private final ClipboardUriSelector clipboardUriSel;

    /**
     * Constructor.
     */
    @Inject
    public ScanQrPresenter(ClipboardUriSelector clipboardUriSelector) {
        this.clipboardUriSel = clipboardUriSelector;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        if (arguments.getString(ARG_LNURL_FLOW) != null) {
            this.lnUrlFlow = LnUrlFlow.valueOf(arguments.getString(ARG_LNURL_FLOW));

            if (lnUrlFlow == LnUrlFlow.STARTED_FROM_RECEIVE) {
                setUpClipboard();
            }
        }
    }

    private void setUpClipboard() {
        final Observable<OperationUri> observable = clipboardUriSel
                .watch()
                .compose(getAsyncExecutor())
                .doOnNext(view::setClipboardUri);

        subscribeTo(observable);
    }

    public void handleResult(String text) {

        if (lnUrlFlow == LnUrlFlow.STARTED_FROM_RECEIVE) {
            if (LnUrl.INSTANCE.isValid(text)) {
                navigator.navigateToLnUrlWithdraw(getContext(), text);

            } else {
                view.onLnUrlScanError(text);
            }
        } else {

            if (LnUrl.INSTANCE.isValid(text)) {
                navigator.navigateToLnUrlWithdrawConfirm(getContext(), text);

            } else {
                newOperationFromScannedText(text);
            }
        }
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
        if (lnUrlFlow != null) {
            return new AnalyticsEvent.S_LNURL_SCAN_QR();

        } else {
            return new AnalyticsEvent.S_SCAN_QR();
        }
    }

    public Unit selectFromUriPaster(@NotNull OperationUri uri) {
        if (uri.getLnUrl().isPresent()) {
            navigator.navigateToLnUrlWithdraw(getContext(), uri.getLnUrl().get());
        }

        view.finishActivity();
        return null;
    }
}
