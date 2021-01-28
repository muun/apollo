package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.presentation.ui.base.BaseActivity;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class DrawerDialogFragment extends BottomSheetDialogFragment
        implements MuunActionDrawer.OnActionClickListener {

    private final BottomSheetBehavior.BottomSheetCallback bottomSheetCallback =
            new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        dismiss();
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                }
            };

    int titleResId;

    protected List<Action> actionList = new ArrayList<>();

    protected MuunActionDrawer actionDrawer;

    private Unbinder butterKnifeUnbinder;

    public DrawerDialogFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();
        getBehavior(actionDrawer).setState(BottomSheetBehavior.STATE_EXPANDED);

    }

    @Override
    public void onPause() {
        super.onPause();
        // Auto-dismiss. We want the fragment to be removed once it goes to the background.
        // This helps us avoid saving state to handle this fragment recreation (HARD) in case
        // activity/fragment gets destroyed while in background.
        dismiss();
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

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        butterKnifeUnbinder = ButterKnife.bind(this, getActivity());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);

        actionDrawer = createActionDrawer();

        if (titleResId > 0) {
            actionDrawer.setTitle(titleResId);
        }

        actionDrawer.setOnItemClickListener(this);

        for (Action action : actionList) {
            actionDrawer.addAction(action.actionId, action.icon, action.iconRes, action.label);
        }

        dialog.setContentView(actionDrawer);
        getBehavior(actionDrawer).setBottomSheetCallback(bottomSheetCallback);

        return dialog;
    }

    @NonNull
    protected MuunActionDrawer createActionDrawer() {
        return new MuunActionDrawer(getContext());
    }

    @Override
    @CallSuper
    public void onDestroyView() {
        butterKnifeUnbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onActionClick(int actionId) {
        ((BaseActivity) getActivity()).onDialogResult(this, actionId, null);
        dismiss();
    }

    private BottomSheetBehavior getBehavior(View content) {
        final CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams)
                ((ViewGroup) content.getParent()).getLayoutParams();

        return (BottomSheetBehavior) layoutParams.getBehavior();
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
