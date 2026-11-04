package io.muun.apollo.presentation.ui.scan_qr;

import io.muun.apollo.R;
import io.muun.apollo.databinding.ScanQrActivityBinding;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel;
import io.muun.apollo.presentation.ui.utils.ExtensionsKt;
import io.muun.apollo.presentation.ui.utils.OS;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewbinding.ViewBinding;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import kotlin.jvm.functions.Function1;
import me.dm7.barcodescanner.zxing.ZXingScannerView;
import timber.log.Timber;

import jakarta.validation.constraints.NotNull;
import java.util.Collections;

public class ScanQrActivity extends SingleFragmentActivity<ScanQrPresenter>
        implements ScanQrView, ZXingScannerView.ResultHandler {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, ScanQrActivity.class);
    }

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntentForLnurl(@NotNull Context context,
                                                        @NotNull LnUrlFlow flow) {
        return getStartActivityIntent(context)
                .putExtra(ARG_LNURL_FLOW, flow.name());
    }

    private ScanQrActivityBinding binding() {
        return (ScanQrActivityBinding) getBinding();
    }

    private View pasteFromClipboardView() {
        return binding().pasteFromClipboard.getRoot();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return ScanQrActivityBinding::inflate;
    }

    private boolean clipboardContainsPlaintext = false;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    public MuunHeader getHeader() {
        return binding().scanQrHeader;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        binding().scanQrHeader.attachToActivity(this);

        final String lnurlFlow = getArgumentsBundle().getString(ARG_LNURL_FLOW);
        if (lnurlFlow != null && LnUrlFlow.valueOf(lnurlFlow) == LnUrlFlow.STARTED_FROM_RECEIVE) {
            binding().scanQrHeader.showTitle(R.string.showqr_title);
            binding().scanQrSubtitle.setText(R.string.scan_lnurl_subtitle);

            binding().uriPaster.setOnSelectListener(presenter::selectFromUriPaster);
        } else {
            binding().scanQrHeader.showTitle(R.string.scanqr_title);
        }

        binding().scanQrHeader.setNavigation(Navigation.BACK);

        binding().emptyScreen.setOnActionClickListener(view -> onGrantPermissionClick());

        if (allPermissionsGranted(Manifest.permission.CAMERA)) {
            showScannerView();
        } else {
            showCameraPermissionPriming();
        }

        setupCamera();

        pasteFromClipboardView().setOnClickListener(v -> presenter.pasteFromClipboard());
    }

    private void showScannerView() {
        // This black rectangle covers the entire view (except for the toolbar), and is shown to
        // hide the camera permission call to action when it isn't necessary.
        binding().scanQrFrameBackground.setVisibility(View.VISIBLE);
        binding().scanQrSubtitle.setVisibility(View.VISIBLE);
        if (OS.supportsClipboardAccessNotification() && clipboardContainsPlaintext) {
            pasteFromClipboardView().setVisibility(View.VISIBLE);
        }
    }

    private void showCameraPermissionPriming() {
        binding().scanQrFrameBackground.setVisibility(View.GONE);
        binding().scanQrSubtitle.setVisibility(View.GONE);
        pasteFromClipboardView().setVisibility(View.GONE);
    }

    private void setupCamera() {
        binding().scanQrScanner.setFormats(Collections.singletonList(BarcodeFormat.QR_CODE));
        binding().scanQrScanner.setAutoFocus(true);
        binding().scanQrScanner.setMaskColor(
                UiUtils.getColorWithAlpha(this, R.color.black, getMaskColorAlpha())
        );

        binding().scanQrScanner.setLaserEnabled(false);
        binding().scanQrScanner.setBorderColor(
                ContextCompat.getColor(getViewContext(), R.color.blue)
        );
        binding().scanQrScanner.setSquareViewFinder(true);

        // NOTE: this line fixed the problem with camera focus in Huawei Mate 9:
        binding().scanQrScanner.setAspectTolerance(0.5f);
    }

    private float getMaskColorAlpha() {
        return ExtensionsKt.isInNightMode(this) ? 0.9f : 0.64f;
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding().scanQrScanner.stopCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding().scanQrScanner.setResultHandler(this);
        binding().scanQrScanner.startCamera(findBackFacingCamera());
    }

    private int findBackFacingCamera() {

        final int numberOfCameras = Camera.getNumberOfCameras();

        for (int i = 0; i < numberOfCameras; i++) {
            final Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void handleResult(Result result) {
        presenter.handleResult(result.getText());
    }

    @Override
    public void onScanError(String text) {
        try {
            showError(new ErrorViewModel.Builder()
                    .loggingName(AnalyticsEvent.ERROR_TYPE.INVALID_QR)
                    .title(getString(R.string.error_op_invalid_address_title))
                    .descriptionRes(R.string.error_op_invalid_address_desc)
                    .descriptionArgs(sanitizeScannedText(text))
                    .build()
            );
        } catch (Throwable error) {
            Timber.e(new RuntimeException(
                    "Could not serialize/handle scanned text: " + sanitizeScannedText(text),
                    error
            ));

            showError(new ErrorViewModel.Builder()
                    .loggingName(AnalyticsEvent.ERROR_TYPE.INVALID_QR)
                    .title(getString(R.string.error_op_invalid_address_title_no_arg))
                    .descriptionRes(R.string.error_op_invalid_address_desc_no_arg)
                    .build()
            );
        }
    }

    @Override
    public void onLnUrlScanError(String text) {
        showError(new ErrorViewModel.Builder()
                .loggingName(AnalyticsEvent.ERROR_TYPE.LNURL_INVALID_CODE)
                .title(getString(R.string.error_invalid_lnurl_title))
                .descriptionRes(R.string.error_invalid_lnurl_desc)
                .descriptionArgs(sanitizeScannedText(text))
                .build()
        );
    }

    private String sanitizeScannedText(String text) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }

        final String trimmedText = text.trim();

        if (TextUtils.isEmpty(trimmedText)) {
            return text;
        }

        // Replace non ascii chars
        final String replacedText = trimmedText
                .replaceAll("[^\\x00-\\x7F]", "");

        if (TextUtils.isEmpty(replacedText)) {
            return replacedText;
        }

        // Crop input to a "sensible" maximum length (e.g. to fit screen size)
        return replacedText
                .substring(0, Math.min(500, replacedText.length()));
    }

    /**
     * Handle enable camera permission click. Request OS permissions use device Camera to be granted
     * to this application.
     */
    public void onGrantPermissionClick() {
        requestPermissions(Manifest.permission.CAMERA);
        presenter.reportCameraPermissionAsked();
    }

    @Override
    public void onPermissionsDenied(String[] deniedPermissions) {
        showCameraPermissionPriming();
    }

    @Override
    public void onPermissionsGranted(String[] grantedPermissions) {
        showScannerView();
        presenter.reportCameraPermissionGranted();

        if (binding().uriPaster.getUri() != null) {
            binding().uriPaster.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void setClipboardStatus(boolean containsPlainText) {
        this.clipboardContainsPlaintext = containsPlainText;

        if (allPermissionsGranted(Manifest.permission.CAMERA)) {
            if (OS.supportsClipboardAccessNotification() && clipboardContainsPlaintext) {
                pasteFromClipboardView().setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void setClipboardUri(@Nullable OperationUri operationUri) {
        binding().uriPaster.setUri(operationUri);
        if (!allPermissionsGranted(Manifest.permission.CAMERA)) {
            binding().uriPaster.setVisibility(View.GONE);
        }
    }

    @Override
    public void showNoLnUrlInClipoardError() {
        showSnackBar(R.string.no_lnurl_in_clipboard_error);
    }
}
