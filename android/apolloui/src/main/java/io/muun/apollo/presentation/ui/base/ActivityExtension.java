package io.muun.apollo.presentation.ui.base;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import icepick.Icepick;

/**
 * ActivityExtension assumes a @PerActivity Dagger Lifetime. Every subclass of ActivityExtension
 * should therefore have the @PerActivity annotation.
 */
public abstract class ActivityExtension {

    private ExtensibleActivity activity;

    private Unbinder butterKnifeUnbinder;

    public void setActivity(ExtensibleActivity activity) {
        this.activity = activity;
        butterKnifeUnbinder = ButterKnife.bind(this, activity);
    }

    protected ExtensibleActivity getActivity() {
        return activity;
    }

    @CallSuper // TODO remove this anti-pattern
    public void onCreate(Bundle savedInstanceState) {
        Icepick.restoreInstanceState(this, savedInstanceState);
    }

    @CallSuper // TODO remove this anti-pattern
    public void onSaveInstanceState(Bundle outState) {
        Icepick.saveInstanceState(this, outState);
    }

    public void onStart() {
    }

    public void onResume() {
    }

    public void onPause() {
    }

    public void onStop() {
    }

    @CallSuper // TODO remove this anti-pattern
    public void onDestroy() {
        butterKnifeUnbinder.unbind();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    }
}