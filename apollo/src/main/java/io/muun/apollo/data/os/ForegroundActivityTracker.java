package io.muun.apollo.data.os;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class ForegroundActivityTracker implements Application.ActivityLifecycleCallbacks {

    private Activity lastForegroundActivity;

    @Inject
    public ForegroundActivityTracker() {

    }

    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    public void onActivityResumed(Activity activity) {
        lastForegroundActivity = activity;
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (activity.equals(lastForegroundActivity)) {
            lastForegroundActivity = null;
        }
    }

    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    public void onActivityDestroyed(Activity activity) {
    }

    public void onActivityStarted(Activity activity) {
    }

    public void onActivityStopped(Activity activity) {
    }

    /**
     * Obtains the current activity in foreground, if it isn't a FragmentActivity or is null, it
     * will throw an exception, that shouldn't happen.
     */
    @NotNull
    public FragmentActivity getForegroundActivityOrThrow() {
        if (lastForegroundActivity instanceof FragmentActivity) {
            return (FragmentActivity) lastForegroundActivity;
        }

        throw new RuntimeException("Last foreground activity is invalid. Activity = "
                + lastForegroundActivity);
    }
}
