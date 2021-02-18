package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import butterknife.BindColor;
import butterknife.BindDrawable;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import com.google.android.material.appbar.MaterialToolbar;

public class FloatingView {

    @BindColor(R.color.background)
    int backgroundColor;

    @BindDrawable(R.drawable.pre_lollipop_popup_background)
    Drawable preLollipopPopupBackground;

    protected final View view;
    protected final android.widget.PopupWindow popupWindow;

    private final Unbinder butterKnifeUnbinder;

    /**
     * Constructor.
     */
    public FloatingView(View view) {
        this.view = view;
        butterKnifeUnbinder = ButterKnife.bind(this, view);

        popupWindow = new android.widget.PopupWindow(
                this.view,
                MaterialToolbar.LayoutParams.WRAP_CONTENT,
                MaterialToolbar.LayoutParams.WRAP_CONTENT,
                true // attach view to viewTree, important for ui tests to work
        );

        if (UiUtils.isLollipop()) {
            popupWindow.setElevation(10);
            popupWindow.setBackgroundDrawable(new ColorDrawable(backgroundColor));

        } else {
            popupWindow.setBackgroundDrawable(preLollipopPopupBackground);
        }

        popupWindow.setOutsideTouchable(true);
    }

    /**
     * <p>
     * Display the content view in a popup window at the specified location.
     * For more info, see {@link PopupWindow#showAtLocation(Context, View, int, int)}.
     * </p>
     *
     * @param parent a parent view to get the {@link android.view.View#getWindowToken()} token from
     * @param gravity the gravity which controls the placement of the popup window
     * @param xOffset the popup's x location offset
     * @param yOffset the popup's y location offset
     * */
    public void show(View parent, int gravity, int xOffset, int yOffset) {
        popupWindow.showAtLocation(parent, gravity, xOffset, yOffset);
    }

    public void dismiss() {
        popupWindow.dismiss();
        butterKnifeUnbinder.unbind();
    }
}
