package io.muun.apollo.presentation.ui.scan_qr;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel;
import io.muun.apollo.presentation.ui.utils.ExtensionsKt;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.MuunEmptyScreen;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.MuunUriPaster;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import butterknife.BindView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import me.dm7.barcodescanner.zxing.ZXingScannerView;
import timber.log.Timber;

import java.util.Collections;
import javax.validation.constraints.NotNull;

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

    @BindView(R.id.empty_screen)
    MuunEmptyScreen emptyScreen;

    @BindView(R.id.scan_qr_header)
    MuunHeader header;

    @BindView(R.id.scan_qr_scanner)
    ZXingScannerView camera;

    @BindView(R.id.scan_qr_subtitle)
    TextView subtitle;

    // This black rectangle covers the entire view (except for the toolbar), and is shown to hide
    // the camera permission call to action when it isn't necessary.
    @BindView(R.id.scan_qr_frame_background)
    FrameLayout background;

    @BindView(R.id.uri_paster)
    MuunUriPaster uriPaster;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.scan_qr_activity;
    }

    @Override
    public MuunHeader getHeader() {
        return header;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);

        final String lnurlFlow = getArgumentsBundle().getString(ARG_LNURL_FLOW);
        if (lnurlFlow != null && LnUrlFlow.valueOf(lnurlFlow) == LnUrlFlow.STARTED_FROM_RECEIVE) {
            header.showTitle(R.string.showqr_title);
            subtitle.setText(R.string.scan_lnurl_subtitle);

            uriPaster.setOnSelectListener(presenter::selectFromUriPaster);
        } else {
            header.showTitle(R.string.scanqr_title);
        }

        header.setNavigation(Navigation.BACK);

        emptyScreen.setOnActionClickListener(view -> onGrantPermissionClick());

        if (allPermissionsGranted(Manifest.permission.CAMERA)) {
            background.setVisibility(View.VISIBLE);
            subtitle.setVisibility(View.VISIBLE);

        } else {
            background.setVisibility(View.GONE);
            subtitle.setVisibility(View.GONE);
        }

        setupCamera();
    }

    private void setupCamera() {
        camera.setFormats(Collections.singletonList(BarcodeFormat.QR_CODE));
        camera.setAutoFocus(true);
        camera.setMaskColor(UiUtils.getColorWithAlpha(this, R.color.black, getMaskColorAlpha()));

        camera.setLaserEnabled(false);
        camera.setBorderColor(ContextCompat.getColor(getViewContext(), R.color.blue));
        camera.setSquareViewFinder(true);

        // NOTE: this line fixed the problem with camera focus in Huawei Mate 9:
        camera.setAspectTolerance(0.5f);
    }

    private float getMaskColorAlpha() {
        return ExtensionsKt.isInNightMode(this) ? 0.9f : 0.64f;
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera.stopCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera.setResultHandler(this);
        camera.startCamera(findBackFacingCamera());
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
        requestPermissions(this, Manifest.permission.CAMERA);
        presenter.reportCameraPermissionAsked();
    }

    @Override
    public void onPermissionsDenied(String[] deniedPermissions) {
        background.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
    }

    @Override
    public void onPermissionsGranted(String[] grantedPermissions) {
        background.setVisibility(View.VISIBLE);
        subtitle.setVisibility(View.VISIBLE);
        presenter.reportCameraPermissionGranted();

        if (uriPaster.getUri() != null) {
            uriPaster.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void setClipboardUri(@Nullable OperationUri operationUri) {
        uriPaster.setUri(operationUri);
        if (!allPermissionsGranted(Manifest.permission.CAMERA)) {
            uriPaster.setVisibility(View.GONE);
        }
    }
}
