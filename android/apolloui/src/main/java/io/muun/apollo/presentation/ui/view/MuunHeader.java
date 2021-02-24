package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.common.exception.MissingCaseError;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import butterknife.BindColor;
import butterknife.BindView;

import javax.annotation.Nullable;

public class MuunHeader extends MuunView {

    static final ViewProps<MuunHeader> viewProps = new ViewProps.Builder<MuunHeader>()
            .addBoolean(R.attr.elevated, MuunHeader::setElevated)
            .addRef(R.attr.titleTextAppearance, MuunHeader::setTitleTextAppearance)
            .build();

    public enum Navigation {
        NONE,
        BACK,
        EXIT
    }

    @BindView(R.id.muun_header_toolbar)
    Toolbar toolbar;

    @BindView(R.id.muun_header_indicator_text)
    TextView indicatorText;

    @BindView(R.id.muun_header_drop_shadow)
    View dropShadow;

    @BindColor(R.color.toolbarColor)
    int defaultBackgroundColor;

    private ActionBar actionBar;

    public MuunHeader(Context context) {
        super(context);
    }

    public MuunHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunHeader(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        setBackgroundColor(defaultBackgroundColor);

        viewProps.transfer(attrs, this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_header;
    }

    @Override
    public void setBackgroundColor(@ColorInt int color) {
        super.setBackgroundColor(color); // if not set on parent, elevation breaks
        toolbar.setBackgroundColor(color);
    }

    /**
     * Configure the navigation button.
     */
    public void setNavigation(Navigation navigation) {
        checkAttached();

        switch (navigation) {
            case NONE:
                setNavigationVisible(false);
                return;

            case BACK:
                setNavigationVisible(true);
                setNavigationIcon(R.drawable.ic_arrow_back);
                return;

            case EXIT:
                setNavigationVisible(true);
                setNavigationIcon(R.drawable.ic_close);
                return;

            default:
                throw new MissingCaseError(navigation);
        }
    }

    /**
     * Show a title, taken from a string resource.
     */
    public void showTitle(@StringRes int titleRes) {
        showTitle(getContext().getString(titleRes));
    }

    /**
     * Show a title, taken as a literal string.
     */
    public void showTitle(String title) {
        checkAttached();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(title);
        toolbar.setTitle(title);
    }

    /**
     * Hide the title.
     */
    public void hideTitle() {
        checkAttached();
        actionBar.setDisplayShowTitleEnabled(false);
        toolbar.setTitle(""); // a little hack, of the kind rarely necessary in Android
    }
    
    /**
     * Set to true to display a drop shadow below the Header.
     */
    public void setElevated(boolean isElevated) {
        dropShadow.setVisibility(isElevated ? View.VISIBLE : View.GONE);
    }

    /**
     * Set Header title.
     */
    public void setTitleTextAppearance(@StyleRes int id) {
        toolbar.setTitleTextAppearance(getContext(), id);
    }

    /**
     * Set the right-hand indicator text (or `null` to remove it).
     */
    public void setIndicatorText(@Nullable CharSequence text) {
        indicatorText.setText(text);
        indicatorText.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    /**
     * Remove all widgets and decorations, leave the header empty.
     */
    public void clear() {
        hideTitle();
        setNavigation(Navigation.NONE);
        setIndicatorText(null);
        setElevated(false);
        toolbar.getMenu().clear();
    }

    /**
     * Attach this Header to provide the ActionBar for an Activity.
     */
    public void attachToActivity(AppCompatActivity activity) {
        activity.setSupportActionBar(toolbar);
        actionBar = activity.getSupportActionBar();
    }

    private void setNavigationVisible(boolean isVisible) {
        actionBar.setDisplayShowHomeEnabled(isVisible);
        actionBar.setDisplayHomeAsUpEnabled(isVisible);
    }

    private void setNavigationIcon(@DrawableRes int iconRes) {
        // Set the icon:
        actionBar.setHomeAsUpIndicator(iconRes);

        // Tint:
        final Drawable drawable = toolbar.getNavigationIcon();
        final int color = ContextCompat.getColor(getContext(), R.color.icon_color);
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    private void checkAttached() {
        if (actionBar == null) {
            throw new IllegalStateException("No ActionBar: attachToActivity() was not called");
        }
    }
}
