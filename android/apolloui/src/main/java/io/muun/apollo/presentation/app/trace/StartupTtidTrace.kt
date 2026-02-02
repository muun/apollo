package io.muun.apollo.presentation.app.trace

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.RequiresApi
import io.muun.apollo.presentation.app.trace.NextDrawImmediateListener.Companion.onNextDrawImmediate
import io.muun.apollo.presentation.app.trace.WindowDelegateCallback.Companion.onDecorViewReady
import io.muun.apollo.presentation.ui.utils.OS
import io.muun.apollo.presentation.ui.utils.addOnNextLayoutListener
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class StartupTtidTrace @Inject constructor(
    private val application: Application,
) : Application.ActivityLifecycleCallbacks {

    companion object {
        /**
         * Object creation timestamp meant to be used as fallback for API < [Build.VERSION_CODES.N].
         */
        private val CLASS_LOAD_TIME = SystemClock.elapsedRealtime()

        fun register(application: Application) {
            application.registerActivityLifecycleCallbacks(StartupTtidTrace(application))
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.window.onDecorViewReady {
            activity.window.decorView.onNextDrawImmediate {
                logTtidAnalyticEvent(
                    elapsedRealtime = SystemClock.elapsedRealtime(),
                    appStartActivity = activity
                )
                application.unregisterActivityLifecycleCallbacks(this)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    /**
     * Logs [ttid](https://developer.android.com/topic/performance/vitals/launch-time#time-initial) metric.
     *
     * **Never ever** send [Activity's intent][Activity.getIntent] related data as it could contain sensitive data.
     */
    private fun logTtidAnalyticEvent(elapsedRealtime: Long, appStartActivity: Activity) {
        Timber.i(
            StringBuilder("[Timing] TTID=${elapsedRealtime - getStartElapsedRealtimeCompat()}")
                .append(", startElapsedRealtimeCompat=${getStartElapsedRealtimeCompat()}")
                .append(", elapsedRealtime=$elapsedRealtime")
                .append(", appStartActivity=${appStartActivity::class.java.simpleName}")
                .toString()
        )
    }

    private fun getStartElapsedRealtimeCompat(): Long {
        return if (OS.supportsProcessStartTimestamps()) {
            Process.getStartElapsedRealtime()
        } else {
            CLASS_LOAD_TIME
        }
    }
}

/**
 * Reports immediately (through [Handler.postAtFrontOfQueue]) the next time the view tree gets drawn.
 * The exposed API is [View.onNextDrawImmediate], all the rest corresponds to internal
 * implementation detail and so it should remain private. The extra extension functions in
 * [NextDrawImmediateListener.Companion] need to be placed in the same scope than [View.onNextDrawImmediate].
 */
private class NextDrawImmediateListener private constructor(
    private val viewReference: AtomicReference<View>,
    private val onDrawCallback: () -> Unit,
) : ViewTreeObserver.OnDrawListener {

    override fun onDraw() {
        val view = viewReference.getAndSet(null) ?: return

        Handler(Looper.getMainLooper()).postAtFrontOfQueue(onDrawCallback)
        view.addOnNextLayoutListener { view.viewTreeObserver.removeOnDrawListener(this) }
    }

    companion object {
        fun View.onNextDrawImmediate(onDrawCallback: () -> Unit) {
            if (OS.affectedByFloatingViewTreeObserverNotMergedBug() && !isAliveAndAttached()) {
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        addNextDrawImmediateListener(onDrawCallback)
                        removeOnAttachStateChangeListener(this)
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        removeOnAttachStateChangeListener(this)
                    }
                })
            } else {
                addNextDrawImmediateListener(onDrawCallback)
            }
        }

        private fun View.isAliveAndAttached(): Boolean {
            return getViewTreeObserver().isAlive && isAttachedToWindow
        }

        private fun View.addNextDrawImmediateListener(callback: () -> Unit) {
            viewTreeObserver.addOnDrawListener(
                NextDrawImmediateListener(
                    AtomicReference(this),
                    callback
                )
            )
        }
    }
}

/**
 * [Window.Callback] wrapper that allows for dynamic registration of custom behaviour when the
 * window's content changes.
 * The exposed API is [Window.onDecorViewReady], all the rest corresponds to internal
 * implementation detail and so it should remain private. The extra extension functions in
 * [WindowDelegateCallback.Companion] need to be placed in the same scope than [Window.onDecorViewReady].
 */
private class WindowDelegateCallback(
    private val wrapped: Window.Callback,
) : Window.Callback by wrapped {

    val onContentChangedCallbacks = mutableListOf<() -> Boolean>()

    override fun onContentChanged() {
        onContentChangedCallbacks.removeAll { callback ->
            !callback()
        }
        wrapped.onContentChanged()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onProvideKeyboardShortcuts(
        data: List<KeyboardShortcutGroup?>?,
        menu: Menu?,
        deviceId: Int,
    ) {
        wrapped.onProvideKeyboardShortcuts(data, menu, deviceId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        wrapped.onPointerCaptureChanged(hasCapture)
    }

    companion object {
        fun Window.onDecorViewReady(callback: () -> Unit) {
            if (peekDecorView() == null) {
                onContentChanged {
                    callback()
                    return@onContentChanged false
                }
            } else {
                callback()
            }
        }

        private fun Window.onContentChanged(block: () -> Boolean) {
            val callback = wrapCallback()
            callback.onContentChangedCallbacks += block
        }

        private fun Window.wrapCallback(): WindowDelegateCallback {
            val currentCallback = callback
            return if (currentCallback is WindowDelegateCallback) {
                currentCallback
            } else {
                val newCallback = WindowDelegateCallback(currentCallback)
                callback = newCallback
                newCallback
            }
        }
    }
}
