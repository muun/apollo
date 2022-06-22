package io.muun.apollo.presentation.ui.utils;

import io.muun.apollo.R;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.utils.DateUtils;
import io.muun.common.utils.Preconditions;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import org.javamoney.moneta.Money;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

import java.lang.ref.WeakReference;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;


public class UiUtils {

    private static final int PREVIEW_AFFIX_LENGTH = 8;

    /**
     * Convert a magnitude in density-independent pixels to pixels.
     */
    public static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Convert a magnitude in scale-independent pixels to pixels.
     */
    public static int spToPx(Context context, int sp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                context.getResources().getDisplayMetrics()
        );
    }

    /**
     * Convert a magnitude in pixels to density-independent pixels.
     */
    public static int pxToDp(Context context, int px) {
        return (int) (px / context.getResources().getDisplayMetrics().density);
    }

    /**
     * Get drawable resource tinted with a specific color, in an "appcompat" way.
     */
    public static Drawable getTintedDrawable(Context context,
                                             @DrawableRes int resourceId,
                                             @ColorRes int colorId) {

        final Drawable drawable = ContextCompat.getDrawable(context, resourceId).mutate();
        setTintColorStateList(drawable, ContextCompat.getColorStateList(context, colorId));

        return drawable;
    }

    /**
     * Get drawable associated with specific state in a
     * {@link android.graphics.drawable.StateListDrawable}, or return if the drawable passed in
     * as parameter is not {@link android.graphics.drawable.StateListDrawable}.
     */
    public static Drawable getDrawableForState(Drawable drawable, int[] state) {
        if (drawable instanceof StateListDrawable) {
            return getDrawableForState((StateListDrawable) drawable, state);

        } else {
            return drawable;
        }
    }

    /**
     * Get drawable associated with specific state in a
     * {@link android.graphics.drawable.StateListDrawable}.
     */
    public static Drawable getDrawableForState(StateListDrawable stateListDrawable, int[] state) {
        final Drawable drawableForState;
        final int[] currentState = stateListDrawable.getState();

        stateListDrawable.setState(state);
        drawableForState = stateListDrawable.getCurrent();
        stateListDrawable.setState(currentState);

        return drawableForState;
    }

    /**
     * Tint an ImageView with specified color resource.
     */
    public static void setTint(ImageView imageView, @ColorRes int colorId) {
        setTint(imageView.getContext(), imageView.getDrawable(), colorId);
    }

    /**
     * Tint a Drawable with specified color resource.
     */
    private static void setTint(Context context, Drawable drawable, @ColorRes int colorId) {
        setTintColor(drawable, ContextCompat.getColor(context, colorId));
    }

    /**
     * Tint a Drawable with specified Android ColorStatelist.
     */
    private static void setTintColorStateList(Drawable drawable, ColorStateList colorStateList) {
        DrawableCompat.setTintList(DrawableCompat.wrap(drawable), colorStateList);
    }

    /**
     * Tint an ImageView with specified Android Color.
     */
    public static void setTintColor(ImageView imageView, @ColorInt int color) {
        setTintColor(imageView.getDrawable(), color);
    }

    /**
     * Tint drawable color. For api < 23 to works drawable need to be CompatDrawable (obtained via
     * ContextCompat.getDrawable())
     */
    public static void setTintColor(Drawable drawable, @ColorInt int color) {
        if (drawable != null) {
            final Drawable compatDrawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(compatDrawable, color);
        }
    }

    /**
     * The only way to successfully tint TextView's compound drawables dynamically.
     */
    public static void setDrawableTint(TextView textView, @ColorInt int color) {
        TextViewCompat.setCompoundDrawableTintList(textView, ColorStateList.valueOf(color));
    }

    /**
     * Set the margins of a View.
     */
    public static void setMargin(View v, int left, int top, int right, int bottom) {
        if (v.getLayoutParams() instanceof MarginLayoutParams) {
            // Any LayoutParams instance that supports margins should work:
            final MarginLayoutParams params = (MarginLayoutParams) v.getLayoutParams();

            params.setMargins(left, top, right, bottom);
            v.requestLayout();
        }
    }

    /**
     * Set the bottom margin of a View.
     */
    public static void setMarginBottom(View v, int bottom) {
        if (v.getLayoutParams() instanceof MarginLayoutParams) {
            // Any LayoutParams instance that supports margins should work:
            final MarginLayoutParams params = (MarginLayoutParams) v.getLayoutParams();

            setMargin(v, params.leftMargin, params.topMargin, params.rightMargin, bottom);
        }
    }

    /**
     * Set a view's left padding.
     */
    public static void setPaddingLeft(View v, int paddingLeft) {
        v.setPadding(paddingLeft, v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom());
    }

    /**
     * Set a view's top padding.
     */
    public static void setPaddingTop(View v, int paddingTop) {
        v.setPadding(v.getPaddingLeft(), paddingTop, v.getPaddingRight(), v.getPaddingBottom());
    }

    /**
     * Set a view's right padding.
     */
    public static void setPaddingRight(View v, int paddingRight) {
        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), paddingRight, v.getPaddingBottom());
    }

    /**
     * Set a view's bottom padding.
     */
    public static void setPaddingBottom(View v, int paddingBottom) {
        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), paddingBottom);
    }

    /**
     * Move the a view up or down as indicated.
     */
    public static void setTranslationY(View v, int offsetY) {
        v.setTranslationY(0f + offsetY);
        v.requestLayout();
    }

    /**
     * Focus an EditText and show the soft keyboard.
     * WARNING: always check if {@link ApplicationLockManager#isLockSet()} before calling to avoid
     * showing keyboard when LockOverlay is shown.
     */
    public static void focusInput(EditText input) {
        input.requestFocus();
        input.setSelection(input.getText().length()); // move cursor to end

        final InputMethodManager imm = (InputMethodManager) input.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        // ShowSoftInput works if the imm's target view is the same that the input we are passing
        // as argument. Target view of imm is set after the focus has been changed by
        // `input.requestFocus()`. I think some post processes exist between these two tasks since
        // one post runnable was not enough.
        // This is ugly AF, but works. Feel free to debug and find a better fix.
        // Example where this is needed: in EditUsernameActivity's onResume.
        input.post(() ->
                input.post(() ->
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                )
        );
    }

    /**
     * These a couple of last-ditch efforts to hide Android's soft keyboard. As you well know we
     * need the view "handling" the keyboard so here we try with a these approaches.
     */
    public static void lastResortHideKeyboard(Activity activity) {
        // Attempt to hide the keyboard immediately:
        tryHideKeyboardHarder(activity);

        // Try again after 100ms (a view may have gained focus after the event loop processed this):
        final WeakReference<Activity> ref = new WeakReference<>(activity);

        new Handler(Looper.getMainLooper()).postDelayed(
                () -> tryHideKeyboardHarder(ref.get()),
                100
        );
    }

    private static void tryHideKeyboardHarder(@Nullable Activity activity) {
        if (activity == null) {
            return;
        }

        // Workaround for the case when the fragment transition was initiated by soft keyboard's
        // Next key. Apparently, tryHideKeyboard with fragment's view does not work, probably
        // because focus changes rapidly to a view that does not belongs to the fragment.
        UiUtils.tryHideKeyboard(activity, activity.getCurrentFocus());

        // Last desperate attempt to hide soft keyboard
        if (activity.getWindow() != null) {
            UiUtils.tryHideKeyboard(activity, activity.getWindow().getDecorView());
        }
    }

    /**
     * Hide devices soft keyboard. Due to Android's awesomeness **cough, cough**, the IMM requires
     * that you specify what View you want to hide the keyboard FROM. So that's why this method
     * receives a View. More details: https://stackoverflow.com/a/17789187/901465
     * Also: https://rmirabelle.medium.com/close-hide-the-soft-keyboard-in-android-db1da22b09d2
     */
    public static void tryHideKeyboard(Context context, View target) {

        if (target == null || context == null) {
            return;
        }

        final InputMethodManager imm = (InputMethodManager) context
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        target.clearFocus();

        if (imm != null) {
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
    }

    /**
     * Shows a view animating over its alpha property.
     *
     * @param view the view to animate.
     */
    public static void fadeIn(View view) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0.1f);
        view.animate().alpha(1f);
    }

    /**
     * Add ripple effect for view.
     */
    public static void setRippleBackground(@NonNull Context context, @NonNull View view) {
        final int[] attrs = new int[]{R.attr.selectableItemBackgroundBorderless};
        final TypedArray ta = context.obtainStyledAttributes(attrs);
        final Drawable drawable = ta.getDrawable(0);
        ta.recycle();

        ViewCompat.setBackground(view, drawable);
    }

    /**
     * Programatically get value from custom style attribute (e.g actionMenuTextColor from
     * MuunActionBarStyle in styles.xml).
     */
    public static int getColorAttrValueFromStyle(@NonNull Context context, @StyleRes int resid,
                                                 int attrId) {

        final int[] attrs = new int[]{attrId};
        final TypedArray ta = context.obtainStyledAttributes(resid, attrs);
        final int color = ta.getColor(0, 0);

        ta.recycle();

        Preconditions.checkState(color != 0);

        return color;
    }

    /**
     * Get an Android Color with alpha, based on a color resource id.
     */
    public static int getColorWithAlpha(@NonNull Context ctx, @ColorRes int colorId, float alpha) {
        final int color = ContextCompat.getColor(ctx, colorId);

        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);

        // Using int version of argb for android retrocompat
        return Color.argb((int) (alpha * 255), r, g, b);
    }

    /**
     * Convert any whitespaces into non breakeable spaces to force a fragment of text into the same
     * line. Bear in mind that if the text is so long that it doesn't fit into a single line, it
     * will get cut/wrapped.
     */
    public static String convertToNonBreakableSpaces(String text) {
        return text.trim().replace(" ", "\u00A0");
    }

    /**
     * Get a short formatted date, intended for list views or summaries.
     */
    public static String getFormattedDate(@NotNull ZonedDateTime zonedDateTime) {
        Preconditions.checkNotNull(zonedDateTime);

        final String timeZoneSuffix = zonedDateTime.getZone().equals(ZoneOffset.UTC) ? " UTC" : "";

        // compute the localized time before checking if it's from today, to avoid a race condition
        // with the sun
        final String localizedTime = zonedDateTime.toLocalTime()
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));

        if (isFromToday(zonedDateTime)) {
            return localizedTime + timeZoneSuffix;
        }

        final String localizedDate = zonedDateTime.toLocalDate()
                .format(DateTimeFormatter.ofPattern("MMM d"));

        if (isFromThisYear(zonedDateTime)) {
            return localizedDate + timeZoneSuffix;
        }

        return zonedDateTime.toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) + timeZoneSuffix;
    }

    /**
     * Get a long formatted date, suitable for detail views.
     */
    public static String getLongFormattedDate(Context context, @NotNull ZonedDateTime dateTime) {
        final LocalDate localDate = dateTime.toLocalDate();
        final LocalTime localTime = dateTime.toLocalTime();

        final String date = localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
        final String time = localTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
        final String timeZone = dateTime.getZone().equals(ZoneOffset.UTC) ? " UTC" : "";

        return context.getString(R.string.long_date_format, date, time.toLowerCase(), timeZone);
    }

    /**
     * Whether this zonedDateTime is from today.
     */
    @VisibleForTesting
    public static boolean isFromToday(ZonedDateTime zonedDateTime) {
        final ZonedDateTime now = getLocalizedNow();

        return zonedDateTime.getYear() == now.getYear()
                && zonedDateTime.getDayOfYear() == now.getDayOfYear();
    }

    /**
     * Whether this zonedDateTime is from this year.
     */
    @VisibleForTesting
    public static boolean isFromThisYear(ZonedDateTime zonedDateTime) {
        return zonedDateTime.getYear() == getLocalizedNow().getYear();
    }

    private static ZonedDateTime getLocalizedNow() {
        return DateUtils.toSystemDefault(ZonedDateTime.now());
    }

    /**
     * Computes the coordinates of this view in its window, according to
     * {@link View#getLocationInWindow(int[])}. Returns a Rect representing the coordinates of
     * the four corners of the view (with regard to the top left corner of the window).
     */
    public static Rect locateViewInWindow(View view) {
        final int[] topLeftCornerLocation = new int[2];

        Preconditions.checkNotNull(view);
        Preconditions.checkState(view.isAttachedToWindow());

        view.getLocationInWindow(topLeftCornerLocation);

        final Rect location = new Rect();

        location.left = topLeftCornerLocation[0];
        location.top = topLeftCornerLocation[1];
        location.right = location.left + view.getWidth();
        location.bottom = location.top + view.getHeight();

        return location;
    }

    /**
     * Ellipsize a, likely long, string (e.g btc address, hash, pub key) for display. Returns a new
     * string with just the first and last characters.
     */
    @NonNull
    public static String ellipsize(String label) {
        return ellipsize(label, PREVIEW_AFFIX_LENGTH);
    }

    /**
     * Ellipsize a, likely long, string (e.g btc address, hash, pub key) for display. Returns a new
     * string with just the first and last characters.
     */
    @NonNull
    public static String ellipsize(String label, int previewAffixLength) {
        if (label.length() <= 2 * previewAffixLength) {
            return label;
        }

        return label.substring(0, previewAffixLength)
                + "..."
                + label.substring(label.length() - previewAffixLength);
    }

    /**
     * Format a double number for display. Limiting decimal digits to 2 but hiding decimal separator
     * and trailing zeros if number is integer. Also, disable grouping separators.
     */
    public static String formatFeeRate(double d) {
        return formatFeeRate(d, RoundingMode.HALF_UP); // DecimalFormat's default rounding mode
    }

    /**
     * Format a double number for display. Limiting decimal digits to 2 but hiding decimal separator
     * and trailing zeros if number is integer. Also, disable grouping separators.
     */
    public static String formatFeeRate(double d, RoundingMode roundingMode) {
        final DecimalFormat formatter = new DecimalFormat();
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(2);
        formatter.setGroupingUsed(false);
        formatter.setRoundingMode(roundingMode);

        return formatter.format(d);
    }

    /**
     * NOTE: DO NOT USE FOR DECISION MAKING LOGIC OR OPERATION CRAFTING.
     * This is strictly-for-display conversion helper. Useful for operation detail screen when we
     * have to display amounts in some currency (e.g primaryCurrency) but we only have the amount
     * in satoshis. As the exact exchange rate is unknown (we would need to know the rate used at
     * the time of the operation), we can use a Rule of 3 using another reference amount for which
     * we known the amount in sats and the amount in the specified currency.
     */
    public static MonetaryAmount convertWithSameRate(
            long amountInSat,
            long referenceInSat,
            MonetaryAmount referenceInCurrency) {

        if (amountInSat == 0) {
            return Money.of(0, referenceInCurrency.getCurrency());
        }

        return referenceInCurrency.multiply(amountInSat / (double) referenceInSat);
    }
}
