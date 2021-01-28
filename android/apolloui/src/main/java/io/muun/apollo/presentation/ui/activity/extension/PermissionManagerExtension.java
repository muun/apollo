package io.muun.apollo.presentation.ui.activity.extension;

import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.utils.Preconditions;

import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import icepick.State;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;

@PerActivity
public class PermissionManagerExtension extends BaseRequestExtension {

    public interface PermissionRequester extends BaseRequestExtension.BaseCaller {

        /**
         * Override this method to be notified when ALL requested permissions have been granted.
         */
        void onPermissionsGranted(String[] grantedPermissions);

        /**
         * Override this method to be notified when SOME requested permissions have been denied.
         */
        void onPermissionsDenied(String[] deniedPermissions);
    }

    /**
     * Ideally, this would go in parent class but for some reason Icepick serialisation doesn't
     * work correctly for children so we store this in both child classes.
     */
    @State(RequestMapBundler.class)
    HashMap<Integer, CallerRequest> pendingRequests = new HashMap<>();

    @Inject
    public PermissionManagerExtension() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Preconditions.checkState(getActivity() instanceof PermissionRequester);
    }

    @Override
    @CallSuper
    public void onRequestPermissionsResult(int globalRequestCode,
                                           String[] permissions,
                                           int[] results) {

        final CallerRequest request = pendingRequests.get(globalRequestCode);
        final PermissionRequester requester = findCaller(request, PermissionRequester.class);

        // Note: It is possible that the permissions request interaction with the user is
        // interrupted. In this case you will receive empty permissions and results arrays which
        // should be treated as a cancellation.

        if (results.length == 0) {
            requester.onPermissionsDenied(permissions);
            return;
        }

        final List<String> permissionsDenied = new ArrayList<>();

        for (int i = 0; i < results.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED) {
                permissionsDenied.add(permissions[i]);
            }
        }

        if (! permissionsDenied.isEmpty()) {
            final String[] array = permissionsDenied.toArray(new String[permissionsDenied.size()]);
            requester.onPermissionsDenied(array);
        } else {
            requester.onPermissionsGranted(permissions);
        }

        pendingRequests.remove(globalRequestCode);
    }

    @Override
    protected void registerRequestFromCaller(CallerRequest request, int globalRequestCode) {
        pendingRequests.put(globalRequestCode, request);
    }

    /**
     * Determine whether you have been granted some permissions.
     */
    public final boolean allPermissionsGranted(String... permissions) {
        for (String permission : permissions) {
            final int grantResult = ContextCompat.checkSelfPermission(getActivity(), permission);

            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    /**
     * Request some permissions to be granted to this application.
     */
    public final void requestPermissions(PermissionRequester requester, String... permissions) {
        final int codeForPermissions = getUniqueCodeForPermissions(permissions);
        final int globalRequestCode = registerRequestFromCaller(requester, codeForPermissions);

        ActivityCompat.requestPermissions(
                getActivity(),
                permissions,
                globalRequestCode
        );
    }

    /**
     * Gets whether you can show UI with rationale for requesting a permission.
     * Return false if the permission was denied with the 'Never ask again' checkbox checked.
     * For more info: https://goo.gl/HxVKYE, https://goo.gl/UkbZzg
     */
    public final boolean canShowRequestPermissionRationale(String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), permission);
    }

    private int getUniqueCodeForPermissions(String[] permissions) {
        // Can only use lower 16 bits for request code
        return Arrays.hashCode(permissions) & 0xFFFF;
    }
}
