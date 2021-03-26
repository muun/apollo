package io.muun.apollo.presentation.ui;

import io.muun.apollo.R;

import android.content.Context;

public abstract class InvoiceExpirationCountdownTimer extends MuunCountdownTimer {
    private static final long MINUTE_IN_SECONDS = 60;
    private static final long HOUR_IN_SECONDS = MINUTE_IN_SECONDS * 60;
    private static final long DAY_IN_SECONDS = HOUR_IN_SECONDS * 24;
    private static final long WEEK_IN_SECONDS = DAY_IN_SECONDS * 7;

    protected final Context ctx;

    public InvoiceExpirationCountdownTimer(Context context, long durationInMillis) {
        super(durationInMillis);
        this.ctx = context;
    }

    @Override
    public void onTickSeconds(long remainingSeconds) {
        final long weeks = remainingSeconds / WEEK_IN_SECONDS;
        final long days = (remainingSeconds % WEEK_IN_SECONDS) / DAY_IN_SECONDS;
        final long hours = (remainingSeconds % DAY_IN_SECONDS) / HOUR_IN_SECONDS;
        final long minutes = (remainingSeconds % HOUR_IN_SECONDS) / MINUTE_IN_SECONDS;
        final long seconds = remainingSeconds % MINUTE_IN_SECONDS;

        final String timeText;
        if (weeks > 0) {
            timeText = ctx.getString(R.string.new_operation_invoice_exp_weeks, weeks, days);

        } else if (days > 0) {
            timeText = ctx.getString(R.string.new_operation_invoice_exp_days, days, hours);

        } else if (hours > 0) {
            timeText = ctx.getString(R.string.new_operation_invoice_exp_hours, hours, minutes);

        } else if (minutes > 0) {
            timeText = ctx.getString(R.string.new_operation_invoice_exp_minutes, minutes, seconds);

        } else {
            timeText = ctx.getString(R.string.new_operation_invoice_exp_seconds, minutes, seconds);
        }

        onTextUpdate(remainingSeconds, timeText);
    }

    /**
     * This won't necessary be a DIFFERENT text on each call. I know, see onTickSeconds javadoc.
     */
    protected abstract void onTextUpdate(long remainingSeconds, CharSequence text);

}
