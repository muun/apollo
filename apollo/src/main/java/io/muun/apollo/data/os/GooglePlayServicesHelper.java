package io.muun.apollo.data.os;

import android.app.Activity;
import android.content.Context;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import rx.functions.Action1;

import javax.validation.constraints.NotNull;

public class GooglePlayServicesHelper {

    public static final int AVAILABLE = ConnectionResult.SUCCESS;

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private final GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();

    /**
     * Check if Google Play Services is installed on the device.
     *
     * @return the result code, which will be AVAILABLE if successful.
     */
    public int isAvailable(@NotNull Context context) {
        return apiAvailability.isGooglePlayServicesAvailable(context);
    }

    /**
     * Display a dialog that allows the user to install Google Play Services.
     */
    public Action1<Activity> showDownloadDialog(int resultCode) {
        return activity -> {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability
                        .getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            }
        };
    }
}
