package io.muun.apollo.presentation.ui

import android.os.CountDownTimer
import java.lang.ref.WeakReference

/**
 * We're going with this particular implementation of CountDownTimer (e.g concrete class + weak
 * reference to an interface) to avoid users from falling in tricky edge cases that lead to
 * non-obvious memory leaks. Our previous impl (abstract class) allowed a callers to easily build an
 * anonymous object implementing the abstract methods which leaked memory on some very specific
 * situations (e.g only when used in our NewOperationActivity, and only upon (ln) payment success,
 * not when going back/aborting the payment). It's apparently related to how kotlin handles internal
 * references to inner anonymous objects, but it's weird that proper disposal of the timer (e.g call
 * cancel + set reference to null in onStop/Destroy) doesn't prevent the mem leak. So, here we are.
 */
class MuunCountdownTimer(durationInMillis: Long, listener: CountDownTimerListener) : CountDownTimer(
    durationInMillis,
    DEFAULT_COUNT_DOWN_INTERVAL_IN_MILLIS
) {

    interface CountDownTimerListener {
        /**
         * NOTE: this won't NECESSARILY be called one time for each remaining second. See
         * countdownInterval above.
         */
        fun onCountDownTick(remainingSeconds: Long)

        fun onCountDownFinish()
    }

    companion object {
        // NOTE: CountDownTimer does something funny. Suppose we want 30 seconds of 1-second
        // ticks. The CountDownTimer should tick 30 times, then finish.

        // Only it most probably won't. It will tick 29 times, because the implementation
        // does not call `onTick` if the remaining time is less than the interval. Given that
        // the main thread scheduler is not millisecond-precise on scheduled callbacks, odds are
        // a little time will be lost here and there, and thus the last tick won't be reported.

        // The solution? Update the counter every 500ms instead, giving the scheduler some room:
        private const val DEFAULT_COUNT_DOWN_INTERVAL_IN_MILLIS = 500L
    }

    private val weakReferenceListener: WeakReference<CountDownTimerListener>

    init {
        this.weakReferenceListener = WeakReference(listener)
    }

    override fun onTick(remainingMillis: Long) {
        val remainingSeconds = remainingMillis / 1000 + 1 // round up to compensate interval
        weakReferenceListener.get()?.onCountDownTick(remainingSeconds)
    }

    override fun onFinish() {
        weakReferenceListener.get()?.onCountDownFinish()
    }
}