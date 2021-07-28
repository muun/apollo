package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import rx.functions.Action0;
import rx.functions.Action1;

import javax.validation.constraints.NotNull;

public interface BaseView {

    @NotNull
    Context getViewContext();

    void showTextToast(String text);

    void showErrorDialog(String errorMessage);

    // JAVAAAA Why don't u default arguments ??!!?
    void showErrorDialog(String errorMessage, Action0 followupAction);

    // JAVAAAA Why don't u default arguments ??!!?
    void showErrorDialog(String errorMessage, Action0 followupAction, Action0 onDismissAction);

    void finishActivity();

    @NotNull
    Bundle getArgumentsBundle();

    void showDialog(MuunDialog dialog);

    void dismissDialog();

    void showPlayServicesDialog(Action1<Activity> activityAction1);
}
