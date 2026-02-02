package io.muun.apollo.presentation.ui.scan_qr;

import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.analytics.NewOperationOrigin;
import io.muun.apollo.domain.libwallet.LnUrl;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.selector.ClipboardUriSelector;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.utils.OS;

import android.os.Bundle;
import icepick.State;
import kotlin.Pair;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import rx.Observable;
import timber.log.Timber;

import javax.inject.Inject;

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

        if (arguments.getString(ScanQrView.ARG_LNURL_FLOW) != null) {
            this.lnUrlFlow = LnUrlFlow.valueOf(arguments.getString(ScanQrView.ARG_LNURL_FLOW));

            if (lnUrlFlow == LnUrlFlow.STARTED_FROM_RECEIVE) {
                setUpClipboard();
            }
        }
    }

    private void setUpClipboard() {
        if (!OS.supportsClipboardAccessNotification()) {
            final Observable<OperationUri> observable = clipboardUriSel
                    .watch()
                    .compose(getAsyncExecutor())
                    .doOnNext(this::setClipboardUri);

            subscribeTo(observable);

        } else {
            final Observable<Boolean> observable = clipboardManager
                    .watchForPlainText()
                    .compose(getAsyncExecutor())
                    .doOnNext(view::setClipboardStatus);

            subscribeTo(observable);

        }
    }

    private void setClipboardUri(OperationUri operationUri) {
        if (operationUri != null && operationUri.isLnUrl()) {
            view.setClipboardUri(operationUri);
        }
    }

    /**
     * Handle QR Code scan result.
     */
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
                    ex,
                    new Pair<String, Object>("text", text)
            ));

            return;
        }

        navigator.navigateToNewOperation(getContext(), origin, uri);
        view.finishActivity();
    }

    /**
     * Track analytics event for Camera permission asked.
     */
    public void reportCameraPermissionAsked() {
        analytics.report(new AnalyticsEvent.E_SCAN_QR_ASK_CAMERA_PERMISSION());
    }

    /**
     * Track analytics event for Camera permission granted.
     */
    public void reportCameraPermissionGranted() {
        analytics.report(new AnalyticsEvent.E_SCAN_QR_CAMERA_PERMISSION_GRANTED());
    }

    /**
     * Get analytics event for this presenter's screen.
     */
    @Override
    protected AnalyticsEvent getEntryEvent() {
        if (lnUrlFlow != null) {
            return new AnalyticsEvent.S_LNURL_SCAN_QR();

        } else {
            return new AnalyticsEvent.S_SCAN_QR();
        }
    }

    /**
     * Select which screen to navigate to, based on the content of an OperationUri.
     */
    public Unit selectFromUriPaster(@NotNull OperationUri uri) {
        if (uri.getLnUrl().isPresent()) {
            navigator.navigateToLnUrlWithdraw(getContext(), uri.getLnUrl().get());

        } else {
            Timber.e(new RuntimeException("Non-LNURL Uri in LNURL UriPaster. Should not happen!"));
        }

        view.finishActivity();
        return null;
    }

    /**
     * Access clipboard and try to navigating to LNURL Withdraw screen, provided clipboard contains
     * a LNURL.
     */
    public void pasteFromClipboard() {
        try {
            final OperationUri operationUri = OperationUri.fromString(clipboardUriSel.getText());

            if (operationUri.getLnUrl().isPresent()) {
                navigator.navigateToLnUrlWithdraw(getContext(), operationUri.getLnUrl().get());

            } else {
                view.showNoLnUrlInClipoardError();
            }

        } catch (IllegalArgumentException e) {
            view.showNoLnUrlInClipoardError();
        }
    }
}
