package io.muun.apollo.presentation.ui;

import android.os.CountDownTimer;

public abstract class MuunCountdownTimer extends CountDownTimer {

    /**
     * Constructor.
     */
    public MuunCountdownTimer(long durationInMillis) {
        // NOTE: CountDownTimer does something funny. Suppose we want 30 seconds of 1-second
        // ticks. The CountDownTimer should tick 30 times, then finish.

        // Only it most probably won't. It will tick 29 times, because the implementation
        // does not call `onTick` if the remaining time is less than the interval. Given that
        // the main thread scheduler is not millisecond-precise on scheduled callbacks, odds are
        // a little time will be lost here and there, and thus the last tick won't be reported.

        // The solution? Update the counter every 500ms instead, giving the scheduler some room:
        super(durationInMillis, 500);
    }

    @Override
    public void onTick(long remainingMillis) {
        final long remainingSeconds = remainingMillis / 1000 + 1; // round up to compensate interval
        onTickSeconds(remainingSeconds);
    }

    /**
     * Yeah, I Know. What a WONDERFUL method name, huh? I couldn't make another abstract onTick
     * method so I had to come up with something, alright? Don't judge me.
     * NOTE: this won't NECESSARILY be called one time for each remaining second. See
     * countdownInterval above.
     */
    public abstract void onTickSeconds(long remainingSeconds);
}
