package io.muun.apollo.presentation.ui.scan_qr;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.view.MuunEmptyScreen;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import butterknife.BindView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

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
        header.showTitle(R.string.scanqr_title);
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
        camera.setMaskColor(ContextCompat.getColor(getViewContext(), R.color.scanner_overlay));

        camera.setLaserEnabled(false);
        camera.setBorderColor(ContextCompat.getColor(getViewContext(), R.color.scanner_border));
        camera.setSquareViewFinder(true);

        // NOTE: this line fixed the problem with camera focus in Huawei Mate 9:
        camera.setAspectTolerance(0.5f);
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

    @SuppressWarnings("deprecation")
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
        presenter.newOperationFromScannedText(result.getText());
    }

    @Override
    public void onScanError(String text) {
        showError(
                R.string.error_op_invalid_address_title,
                R.string.error_op_invalid_address_desc,
                sanitizeScannedText(text)
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
    }
}
