package io.muun.apollo.presentation.ui.helper;

import io.muun.apollo.presentation.app.di.PerApplication;

import android.content.Context;
import android.content.res.Resources;
import androidx.annotation.StringRes;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerApplication
public class StringsHelper {

    private final Resources resources;

    @Inject
    StringsHelper(Context context) {
        this.resources = context.getResources();
    }

    @NotNull
    public String getString(@StringRes int resourceId) {
        return resources.getString(resourceId);
    }

    @NotNull
    public String getString(@StringRes int resourceId, Object... formatArgs) {
        return resources.getString(resourceId, formatArgs);
    }
}
