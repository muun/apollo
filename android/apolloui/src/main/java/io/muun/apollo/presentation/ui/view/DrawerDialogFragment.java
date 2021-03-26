package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.presentation.ui.base.BaseActivity;

import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DrawerDialogFragment extends MuunBottomSheetDialogFragment
        implements MuunActionDrawer.OnActionClickListener {

    int titleResId;

    protected List<Action> actionList = new ArrayList<>();

    public DrawerDialogFragment() {
    }

    public DrawerDialogFragment setTitle(int resId) {
        titleResId = resId;
        return this;
    }

    /**
     * Add an Action to this ActionDrawerDialog.
     */
    public DrawerDialogFragment addAction(int actionId, String label) {
        final Action action = buildAction(actionId, label);
        actionList.add(action);
        return this;
    }

    /**
     * Add an Action to this ActionDrawerDialog.
     */
    public DrawerDialogFragment addAction(int actionId, @DrawableRes int iconRes, String label) {
        final Action action = buildAction(actionId, label);
        action.iconRes = iconRes;
        actionList.add(action);
        return this;
    }

    /**
     * Add an Action to this ActionDrawerDialog.
     */
    public DrawerDialogFragment addAction(int actionId, Drawable icon, String label) {
        final Action action = buildAction(actionId, label);
        action.icon = icon;
        actionList.add(action);
        return this;
    }

    @NonNull
    private Action buildAction(int actionId, String label) {
        final Action action = new Action();
        action.actionId = actionId;
        action.label = label;
        return action;
    }

    @NotNull
    @Override
    protected View createContentView() {
        final MuunActionDrawer actionDrawer = createActionDrawer();

        if (titleResId > 0) {
            actionDrawer.setTitle(titleResId);
        }

        actionDrawer.setOnItemClickListener(this);

        for (Action action : actionList) {
            actionDrawer.addAction(action.actionId, action.icon, action.iconRes, action.label);
        }

        return actionDrawer;
    }

    @NonNull
    protected MuunActionDrawer createActionDrawer() {
        return new MuunActionDrawer(getContext());
    }

    @Override
    public void onActionClick(int actionId) {
        ((BaseActivity) getActivity()).onDialogResult(this, actionId, null);
        dismiss();
    }

    private static class Action {
        private int actionId;
        private String label;

        // NOTE:
        // One of the following two fields should be set:

        // Custom Drawable, when an asset was dynamically loaded after the Drawer was initialized.
        @Nullable
        private Drawable icon;

        // Drawable resource, when a bundled asset was specified during Fragment construction.
        @Nullable
        private Integer iconRes;
    }
}
