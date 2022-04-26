package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.StringRes;
import rx.functions.Action0;
import rx.functions.Action1;

import javax.validation.constraints.NotNull;

public interface BaseView {

    /**
     * Get Android context.
     */
    @NotNull
    Context getViewContext();

    /**
     * Safely try and show a simple test Toast.
     * You should not call this method from any thread other than Android's MainThread. It's a
     * coding error. We're just adding a safety measure to avoid fatal crashes in weird/edge
     * situations.
     */
    void showTextToast(String text);

    /**
     * Show a simple, standard muun error dialog.
     */
    void showErrorDialog(@StringRes int resourceId);

    // JAVAAAA Why don't u default arguments ??!!?
    /**
     * Show a simple, standard muun error dialog.
     */
    void showErrorDialog(@StringRes int resourceId, Action0 followupAction);

    /**
     * Show a simple, standard muun error dialog.
     */
    void showErrorDialog(CharSequence errorMessage);

    // JAVAAAA Why don't u default arguments ??!!?
    /**
     * Show a simple, standard muun error dialog.
     */
    void showErrorDialog(CharSequence errorMessage, Action0 followupAction);

    // JAVAAAA Why don't u default arguments ??!!?
    /**
     * Show a simple, standard muun error dialog.
     */
    void showErrorDialog(CharSequence errorMessage, Action0 followup, Action0 onDismiss);

    /**
     * Immediately finish this View's associated Activity.
     */
    void finishActivity();

    /**
     * Get this view's arguments/Intent bundle.
     */
    @NotNull
    Bundle getArgumentsBundle();

    /**
     * Show an AlertDialog.
     */
    void showDialog(MuunDialog dialog);

    /**
     * Dismiss currently dislayed AlertDialog, if any.
     */
    void dismissDialog();

    /**
     * Display a dialog that allows the user to install Google Play Services.
     */
    void showPlayServicesDialog(Action1<Activity> activityAction1);
}
