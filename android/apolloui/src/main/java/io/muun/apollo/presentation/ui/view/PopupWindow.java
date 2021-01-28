package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.widget.Toolbar;
import butterknife.BindColor;
import butterknife.BindDrawable;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import rx.functions.Action0;

import javax.annotation.Nonnull;

public class PopupWindow {

    @BindColor(R.color.muun_white)
    int backgroundColor;

    @BindDrawable(R.drawable.pre_lollipop_popup_background)
    Drawable preLollipopPopupBackground;

    private final android.widget.PopupWindow popupWindow;
    private final View layoutView;
    private final Unbinder butterKnifeUnbinder;

    /**
     * Constructor.
     */
    public PopupWindow(Activity activity, @LayoutRes int layout) {

        layoutView = activity.getLayoutInflater().inflate(layout, null);

        butterKnifeUnbinder = ButterKnife.bind(this, layoutView);

        popupWindow = new android.widget.PopupWindow(
                layoutView,
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT
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
     * Add an onClick handler for a view in this PopupWindow.
     */
    //TODO: If needed, we could overload bindViewClick to handle callbacks that receive arguments
    public void bindViewClick(@IdRes int viewId, Action0 onClick) {
        layoutView.findViewById(viewId).setOnClickListener(v -> {
            onClick.call();
            popupWindow.dismiss();
        });
    }

    /**
     * See {@link android.widget.PopupWindow#showAtLocation(View, int, int, int)}.
     * NOTE: In contrast to {@link android.widget.PopupWindow#showAtLocation(View, int, int, int)},
     * this method receives horizontal and vertical offsets in DP (and does the conversion to
     * pixels).
     */
    public void showAtLocation(@Nonnull Context context,
                               @Nonnull View parent,
                               int xOffsetInDp,
                               int yOffsetInDp) {
        popupWindow.showAtLocation(
                parent,
                Gravity.TOP | Gravity.END,
                UiUtils.dpToPx(context, xOffsetInDp),
                UiUtils.dpToPx(context, yOffsetInDp)
        );
    }

    public void dismiss() {
        popupWindow.dismiss();
    }

    public void tearDownUi() {
        butterKnifeUnbinder.unbind();
    }
}
