package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import android.content.Context;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import butterknife.BindView;

import javax.annotation.Nullable;

public class MuunDetailItem extends MuunView {

    static final ViewProps<MuunDetailItem> viewProps = new ViewProps.Builder<MuunDetailItem>()
            .addString(R.attr.title, MuunDetailItem::setTitle)
            .addString(R.attr.description, MuunDetailItem::setDescription)
            .addInt(android.R.attr.maxLines, MuunDetailItem::setMaxLines)
            .addRef(R.attr.icon, MuunDetailItem::setIcon)
            .build();

    @BindView(R.id.operation_detail_item_icon_title)
    ImageView titleIcon;

    @BindView(R.id.operation_detail_item_text_title)
    TextView title;

    @BindView(R.id.operation_detail_item_text_content)
    TextView description;

    @BindView(R.id.operation_detail_item_icon_frame)
    ViewGroup iconFrame;

    @BindView(R.id.operation_detail_item_icon)
    ImageView icon;

    private CustomLinkMovementMethod linkMovementMethod;

    public MuunDetailItem(Context context) {
        super(context);
    }

    public MuunDetailItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunDetailItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_detail_item;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        linkMovementMethod = new CustomLinkMovementMethod();
        description.setMovementMethod(linkMovementMethod);

        viewProps.transfer(attrs, this);
    }

    public void setTitle(CharSequence titleText) {
        title.setText(titleText);
    }

    public void setTitleSize(float size) {
        title.setTextSize(size);
    }

    public void setTitleIcon(@DrawableRes int resId) {
        if (resId != 0) {
            titleIcon.setImageResource(resId);
            titleIcon.setVisibility(VISIBLE);

        } else {
            titleIcon.setVisibility(GONE);
        }
    }

    public void setTitleIconTint(@ColorRes int colorId) {
        UiUtils.setTint(titleIcon, colorId);
    }

    /**
     * Set the long-click listener for this item.
     */
    public void setOnLongClickListener(OnLongClickListener listener) {
        // Android strikes again!
        // There's a number of problems with just setting the long-click listener in this view.

        // First, since children are also clickable, they intercept the event and it never reaches
        // this view. So, instead of adding the listener to the outer frame, we'll add it to each
        // relevant child.

        // Second, links contained in the description text always capture clicks. Just setting the
        // listener will process the long-click, and then *also* trigger the link click. To work
        // around this, we'll wrap the listener and disable links while the long-click is handled.

        final OnLongClickListener wrappedListener = (view) -> {
            linkMovementMethod.ignoreNextTouchEvent = true;
            return listener.onLongClick(view);
        };

        description.setLongClickable(true);
        description.setOnLongClickListener(wrappedListener);

        title.setLongClickable(true);
        title.setOnLongClickListener(wrappedListener);
    }

    public void setOnIconClickListener(OnClickListener listener) {
        iconFrame.setOnClickListener(listener);
    }

    /**
     * Set and show description (if not null/empty).
     */
    public void setDescription(CharSequence descriptionText) {
        if (!TextUtils.isEmpty(descriptionText)) {
            description.setText(descriptionText);
            description.setVisibility(VISIBLE);
        }
    }

    public void hideDescription() {
        description.setVisibility(GONE);
    }

    private void setMaxLines(Integer maxLines) {
        description.setMaxLines(maxLines);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setMovementMethod(null); // LinkMovementMethod not compat with ellipse/maxLines
    }

    public void setIcon(@DrawableRes int resId) {
        icon.setImageResource(resId);
        iconFrame.setVisibility(VISIBLE);
    }

    /**
     * A LinkMovementMethod for TextViews that allows ignoring touch events.
     */
    private class CustomLinkMovementMethod extends LinkMovementMethod {
        boolean ignoreNextTouchEvent = false;

        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            if (ignoreNextTouchEvent) {
                ignoreNextTouchEvent = false;
                return false;

            } else {
                return super.onTouchEvent(widget, buffer, event);
            }
        }
    }
}
