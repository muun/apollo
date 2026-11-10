package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.databinding.MuunActionDrawerBinding;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.viewbinding.ViewBinding;
import icepick.State;
import kotlin.jvm.functions.Function3;

import java.util.HashMap;

public class MuunActionDrawer extends MuunView<ViewBinding> {

    public interface OnActionClickListener {
        void onActionClick(int actionId);
    }

    @State
    HashMap<Integer, Integer> actionIdToViewIndex = new HashMap<>();

    private OnActionClickListener listener;

    public MuunActionDrawer(Context context) {
        super(context);
    }

    public MuunActionDrawer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunActionDrawer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return MuunActionDrawerBinding::inflate;
    }

    @Nullable
    protected ViewGroup getContainer() {
        return null;
    }

    @Nullable
    protected TextView getDrawerTitle() {
        return null;
    }

    public void setOnItemClickListener(OnActionClickListener listener) {
        this.listener = listener;
    }

    /**
     * Show the drawer title from a string resource.
     */
    public void setTitle(@StringRes int resId) {
        final TextView title = getDrawerTitle();
        Preconditions.checkNotNull(title);
        title.setText(resId);
        title.setVisibility(VISIBLE);
    }

    /**
     * Show the drawer title from a literal char sequence.
     */
    public void setTitle(CharSequence text) {
        final TextView title = getDrawerTitle();
        Preconditions.checkNotNull(title);
        title.setText(text);
        title.setVisibility(VISIBLE);
    }

    /**
     * Add an action item to this drawer.
     */
    public void addAction(int actionId,
                          @Nullable Drawable maybeIcon,
                          @Nullable @DrawableRes Integer maybeIconRes,
                          String label) {

        final ViewGroup container = getContainer();
        Preconditions.checkNotNull(container);

        final MuunActionDrawerItem item = new MuunActionDrawerItem(getContext());

        if (maybeIcon != null) {
            item.setIcon(maybeIcon);
        } else if (maybeIconRes != null) {
            item.setIcon(maybeIconRes);
        }

        item.setLabel(label);
        item.setOnClickListener(v -> reportItemClick(actionId));

        actionIdToViewIndex.put(actionId, container.getChildCount());
        container.addView(item);
    }

    /**
     * Update the label of an action previously added with {@link #addAction}.
     */
    public void setActionLabel(int actionId, String text) {
        final MuunActionDrawerItem item = getItem(actionId);
        if (item != null) {
            item.setLabel(text);
        }
    }

    /**
     * Enable or disable an action previously added with {@link #addAction}.
     */
    public void setActionEnabled(int actionId, boolean enabled) {
        final MuunActionDrawerItem item = getItem(actionId);
        if (item != null) {
            item.setEnabled(enabled);
        }
    }

    private MuunActionDrawerItem getItem(int actionId) {
        final ViewGroup container = getContainer();
        Preconditions.checkNotNull(container);

        final Integer index = actionIdToViewIndex.get(actionId);
        if (index != null) {
            final View child = container.getChildAt(index);

            Preconditions.checkState(child instanceof MuunActionDrawerItem);

            return (MuunActionDrawerItem) child;
        }
        return null;
    }

    private void reportItemClick(int actionId) {
        if (listener != null) {
            listener.onActionClick(actionId);
        }
    }
}
