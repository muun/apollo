package io.muun.apollo.data.logging;

import io.muun.apollo.domain.errors.ErrorProcessingError;

import rx.exceptions.AssemblyStackTraceException;

public class CrashReportingUtils {

    private static final String INCLUDE = "io.muun";

    private static final String[] EXCLUDE_ALL = new String[] {
            // Not an opinionated list. Built by observation.
            "io.muun.common.rx.ObservableFn",
            "io.muun.apollo.data.net.base.RxCallAdapterWrapper",
            "io.muun.apollo.data.net.base.-$$Lambda$RxCallAdapterWrapper",
            "io.muun.apollo.data.os.execution.ExecutionTransformerFactory"
    };

    private static final StackTraceBuilder stackTraceBuilder = new StackTraceBuilder(
            INCLUDE,
            EXCLUDE_ALL
    );

    /**
     * Create a SummarizedException that (for Rx errors) collapses all causes into a single stack,
     * eliminates noise and de-duplicates entries. Other errors are not affected.
     *
     * <p>This makes Crashlytics correctly list the problem with a useful file:line origin, and
     * removes the aggresive grouping problem with OnSubscribeMap and friends.
     */
    public static Throwable summarize(Throwable error) {
        // TEMP: a little failsafe in case of dark magic failure. We'll remove this when we feel
        // confident about this code. Or never, knowing how these things tend to go.
        try {
            return doSummarize(error);

        } catch (Exception errorDuringSummary) {
            Logger.rawError(new ErrorProcessingError("Failure during summary", error));
            return error;
        }
    }

    private static Throwable doSummarize(Throwable error) {
        if (error instanceof SummarizedException || !hasRxAssemblyException(error)) {
            return error;
        }

        return new SummarizedException(error, stackTraceBuilder.createRelevantStackTrace(error));
    }

    private static boolean hasRxAssemblyException(Throwable error) {
        Throwable cause = error;

        while (cause != null) {
            if (cause instanceof AssemblyStackTraceException) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

}
