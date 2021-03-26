package io.muun.apollo.presentation.ui.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import io.muun.apollo.R
import timber.log.Timber

val ViewGroup.children get() =
    (0 until childCount).map { getChildAt(it) }


fun View.addOnNextLayoutListener(f: () -> Unit) {
    val listener = object: ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            f()
        }
    }

    viewTreeObserver.addOnGlobalLayoutListener(listener)
}

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