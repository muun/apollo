package io.muun.apollo.presentation.ui.utils

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.*
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import io.muun.apollo.R
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.base.ExtensibleActivity
import timber.log.Timber
import java.util.*

val ViewGroup.children: List<View>
    get() =
        (0 until childCount).map { getChildAt(it) }

fun View.addOnNextLayoutListener(f: () -> Unit) {
    val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            f()
        }
    }

    viewTreeObserver.addOnGlobalLayoutListener(listener)
}

/**
 * Attaches a child view to a parent ViewGroup at a specified origin in millimeters,
 * with optional positional modifications. If the origin is null, the view is centered
 * so that its center aligns with the parent’s center.
 *
 * @param childView The view to be attached.
 * @param originXmm The X-axis origin in millimeters, or null to center horizontally.
 * @param originYmm The Y-axis origin in millimeters, or null to center vertically.
 * @param modifications An optional lambda to modify the X and Y positions in pixels.
 *                      If null, the positions are used as-is.
 */
fun ViewGroup.attachChildAtMm(
    childView: View,
    originXmm: Float? = null,
    originYmm: Float? = null,
    modifications: ((Float) -> Float)? = null,
) {
    addView(childView)

    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (childView.width > 0 && childView.height > 0 && width > 0 && height > 0) {
                viewTreeObserver.removeOnGlobalLayoutListener(this)

                val originXpx = originXmm?.mmToPx(resources = context.resources)
                    ?: ((width / 2f) - (childView.width / 2f))

                val originYpx = originYmm?.mmToPx(resources = context.resources)
                    ?: ((height / 2f) - (childView.height / 2f))

                childView.x = modifications?.invoke(originXpx) ?: originXpx
                childView.y = modifications?.invoke(originYpx) ?: originYpx
            }
        }
    })
}

/**
 * Sets the size of a View using dimensions provided in millimeters.
 *
 * @receiver The view to resize.
 * @param widthMm The desired width in millimeters.
 * @param heightMm The desired height in millimeters.
 */
fun View.setSizeInMillimeters(widthMm: Float, heightMm: Float) {
    layoutParams = layoutParams.apply {
        width = widthMm.mmToPx(resources = context.resources).toInt()
        height = heightMm.mmToPx(resources = context.resources).toInt()
    }
}

/**
 * Positions a View on the screen using millimeter-based translation, with an optional offset.
 *
 * @receiver The view to be positioned.
 * @param originXmm The X-axis origin in millimeters.
 * @param originYmm The Y-axis origin in millimeters.
 * @param offsetMm An optional offset in millimeters applied to both axes. Default is 10mm.
 */
fun View.positionInMillimeters(
    originXmm: Float,
    originYmm: Float,
    offsetMm: Float = 10f,
) {
    translationX = originXmm.mmToPx(offsetMm, context.resources)
    translationY = originYmm.mmToPx(offsetMm, context.resources)
}

/**
 * Converts a Float value from millimeters to pixels, optionally adding an offset in millimeters.
 *
 * @receiver The millimeter value to convert.
 * @param offsetMm An optional offset in millimeters to include in the conversion. Default is 0.
 * @return The converted value in pixels.
 */
fun Float.mmToPx(offsetMm: Float = 0f, resources: Resources): Float {
    val displayMetrics = resources.displayMetrics
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_MM,
        (this + offsetMm),
        displayMetrics,
    )
}

/**
 * Triggers a short vibration on the device using the system's [Vibrator] service.
 */
internal fun Context.vibrateShort() {
    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    vibrator.vibrate(50)
}

fun View.setUserInteractionEnabled(enabled: Boolean) {
    isEnabled = enabled
    if (this is ViewGroup && this.childCount > 0) {
        this.children.iterator().forEach {
            it.setUserInteractionEnabled(enabled)
        }
    }
}

fun View.locale(): Locale =
    context.locale()

fun Fragment.getDrawable(@DrawableRes resId: Int) =
    ContextCompat.getDrawable(activity!!, resId)!!

fun View.getDrawable(@DrawableRes resId: Int) =
    ContextCompat.getDrawable(context!!, resId)!!

fun Fragment.getStyledString(@StringRes resId: Int, vararg args: String) =
    StyledStringRes(requireContext(), resId).toCharSequence(*args)

fun Activity.getStyledString(@StringRes resId: Int, vararg args: String) =
    StyledStringRes(this, resId).toCharSequence(*args)

fun View.getStyledString(@StringRes resId: Int, vararg args: String) =
    StyledStringRes(context, resId).toCharSequence(*args)

/**
 * Returns whether the device supports night mode or not.
 */
fun supportsDarkMode(): Boolean =
    OS.supportsDarkMode()

fun postDelayed(delayInMillis: Long, runnable: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed(runnable, delayInMillis)
}

/**
 * Returns whether night mode is active or not.
 */
fun Activity.isInNightMode(): Boolean =
    getCurrentNightMode() == Configuration.UI_MODE_NIGHT_YES

/**
 * Return whether night mode is active or not.
 *
 * Sadly, the answer from the OS, in theory, could be, undefined :s. From the docs:
 *
 * <p>The {@link #UI_MODE_NIGHT_MASK} defines whether the screen
 * is in a special mode. They may be one of {@link #UI_MODE_NIGHT_UNDEFINED},
 * {@link #UI_MODE_NIGHT_NO} or {@link #UI_MODE_NIGHT_YES}.
 *
 * Apparently, we should assume night mode is NOT active if answer is undefined, so that's what
 * we'll do (also it helps to be consistent).
 * https://medium.com/androiddevelopers/appcompat-v23-2-daynight-d10f90c83e94
 */
fun Activity.getCurrentNightMode(): Int {
    val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    if (uiMode == Configuration.UI_MODE_NIGHT_UNDEFINED) {
        return Configuration.UI_MODE_NIGHT_NO
    }

    return uiMode
}

/**
 * Returns whether night mode is active or not.
 */
fun Fragment.isInNightMode(): Boolean =
    getCurrentNightMode() == Configuration.UI_MODE_NIGHT_YES

/**
 * Return whether night mode is active or not.
 *
 * Sadly, the answer from the OS, in theory, could be, undefined :s. From the docs:
 *
 * <p>The {@link #UI_MODE_NIGHT_MASK} defines whether the screen
 * is in a special mode. They may be one of {@link #UI_MODE_NIGHT_UNDEFINED},
 * {@link #UI_MODE_NIGHT_NO} or {@link #UI_MODE_NIGHT_YES}.
 *
 * Apparently, we should assume night mode is NOT active if answer is undefined, so that's what
 * we'll do (also it helps to be consistent).
 * https://medium.com/androiddevelopers/appcompat-v23-2-daynight-d10f90c83e94
 */
fun Fragment.getCurrentNightMode(): Int {
    val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    if (uiMode == Configuration.UI_MODE_NIGHT_UNDEFINED) {
        return Configuration.UI_MODE_NIGHT_YES
    }

    return uiMode
}

/**
 * Returns whether night mode is active or not.
 */
fun View.isInNightMode(): Boolean =
    getCurrentNightMode() == Configuration.UI_MODE_NIGHT_YES

/**
 * Return whether night mode is active or not.
 *
 * Sadly, the answer from the OS, in theory, could be, undefined :s. From the docs:
 *
 * <p>The {@link #UI_MODE_NIGHT_MASK} defines whether the screen
 * is in a special mode. They may be one of {@link #UI_MODE_NIGHT_UNDEFINED},
 * {@link #UI_MODE_NIGHT_NO} or {@link #UI_MODE_NIGHT_YES}.
 *
 * Apparently, we should assume night mode is NOT active if answer is undefined, so that's what
 * we'll do (also it helps to be consistent).
 * https://medium.com/androiddevelopers/appcompat-v23-2-daynight-d10f90c83e94
 */
fun View.getCurrentNightMode(): Int {
    val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    if (uiMode == Configuration.UI_MODE_NIGHT_UNDEFINED) {
        return Configuration.UI_MODE_NIGHT_YES
    }

    return uiMode
}

fun Context.string(@StringRes id: Int) =
    resources.getString(id)

fun Context.string(@StringRes id: Int, vararg formatArgs: Any) =
    resources.getString(id, *formatArgs)

fun Context.notificationManager(): NotificationManager =
    systemService(Context.NOTIFICATION_SERVICE)

@Suppress("UNCHECKED_CAST")
fun <T> Context.systemService(name: String): T =
    getSystemService(name) as T

/**
 * Dangerous. Might throw ActivityNotFoundException if there's no application to handle the intent.
 * Caller is responsible for handling this error.
 */
fun Context.openUri(uriString: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))

    // Flag needed to avoid crash on Android 6 when starting activity from non-activity context
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

    startActivity(intent)
}

fun Context.openInBrowser(uriString: String) {
    try {
        openUri(uriString)
    } catch (e: ActivityNotFoundException) {
        Timber.e("Browser client not found!")
        val message: String = getString(R.string.error_no_web_client_installed)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        // TODO avoid toast and show a proper dialog. We should use AlertDialogExtension but for
        //  that we need and activity. We should move this extension to another place and refactor
        // LinkBuilder too. RabbitHole Alert!!!
    }
}

fun Animation.setOnEndListener(f: () -> Unit) {
    setAnimationListener(object : Animation.AnimationListener {

        override fun onAnimationStart(animation: Animation) {
        }

        override fun onAnimationEnd(animation: Animation) {
            f()
        }

        override fun onAnimationRepeat(animation: Animation) {
        }
    })
}

fun TextView.setTextAppearanceCompat(@StyleRes resId: Int) {
    TextViewCompat.setTextAppearance(this, resId)
}

fun TextView.setDrawableTint(@ColorInt color: Int) {
    UiUtils.setDrawableTint(this, color)
}

fun Context.getColorCompat(@ColorRes colorId: Int) =
    ContextCompat.getColor(this, colorId)

fun TextView.setStyledText(@StringRes resId: Int, vararg args: Any) {
    setStyledText(resId, {}, *args)
}

fun TextView.setStyledText(
    @StringRes resId: Int,
    onLinkClick: (String) -> Unit = {},
    vararg args: Any,
) {
    StyledStringRes(context, resId, onLinkClick)
        .toCharSequence(*args.map { it.toString() }.toTypedArray())
        .let(::setText)
}

/**
 * WARNING: Starting with Android 11 (API level 30) queryIntentActivities() requires the app
 * the declare the package visibility that it needs in the AndroidManifest. Otherwise SO will
 * filter apps/packages in the response. (https://developer.android.com/training/package-visibility)
 */
fun PackageManager.hasAppInstalled(intent: Intent) =
    queryIntentActivities(intent, 0).size != 0

fun FragmentTransaction.safelyCommitNow(activity: ExtensibleActivity) {
    if (!activity.isFinishing && !activity.isDestroyed) {
        val fragmentManager = activity.supportFragmentManager
        if (!fragmentManager.isDestroyed && !fragmentManager.isStateSaved) {
            commitNow()
        }
    }
}