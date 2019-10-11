package io.muun.apollo.data.logging;

import io.muun.common.exception.HttpException;

import com.crashlytics.android.Crashlytics;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.money.CurrencyQuery;
import javax.money.CurrencyQueryBuilder;
import javax.money.UnknownCurrencyException;
import javax.money.spi.Bootstrap;
import javax.money.spi.CurrencyProviderSpi;

public final class Logger {

    private static boolean logToCrashlytics = true;

    private static final int CRASHLYTICS_LAST_REQUESTS_AMOUNT = 5;
    private static final Map<String, String> idempotencyKeysToUris = new HashMap<>();
    private static final List<String> lastRequests = new ArrayList<>();

    private Logger() {
        throw new AssertionError();
    }

    public static void setLogToCrashlytics(boolean logToCrashlytics) {
        Logger.logToCrashlytics = logToCrashlytics;
    }

    public static void configureForUser(String userId, String email) {
        Crashlytics.setUserIdentifier(userId);
        Crashlytics.setUserName(email);
    }

    private static void logRemoteException(Throwable original, Throwable summarized) {

        if (!logToCrashlytics) {
            return;
        }

        if (original instanceof HttpException) {

            final HttpException httpException = (HttpException) original;

            Crashlytics.setLong("requestId", httpException.getRequestId());
            Crashlytics.setInt("errorCode", httpException.getErrorCode().getCode());
            Crashlytics.setString("developerMessage", httpException.getDeveloperMessage());
        }

        if (original instanceof UnknownCurrencyException) {

            final CurrencyQuery query = CurrencyQueryBuilder.of()
                    .setCurrencyCodes("ARS")
                    .build();

            final Collection<CurrencyProviderSpi> services =
                    Bootstrap.getServices(CurrencyProviderSpi.class);

            Crashlytics.setInt("size(CurrencyProviderSpi[])", services.size());
            Crashlytics.setString("CurrencyProviderSpi[]", Arrays.toString(services.toArray()));

            int i = 0;
            for (CurrencyProviderSpi spi : services) {
                i++;
                Crashlytics.setString("Service " + i, spi.getClass().getCanonicalName());
                Crashlytics.setBool("Has currency " + i, spi.isCurrencyAvailable(query));
            }
        }

        if (original.getStackTrace() == null) {
            Crashlytics.setString("Null stacktrace", original.getClass().getCanonicalName());
            // pretty please fill that stack trace
            original.fillInStackTrace();
        }

        // TEMP: we're logging BOTH versions, just to transition without losing info:
        Crashlytics.logException(original);

        if (summarized != original) {
            Crashlytics.logException(summarized);
        }
    }

    private static void logRemoteException(Throwable throwable,
                                           Throwable summarized,
                                           String message,
                                           Object... args) {

        if (!logToCrashlytics) {
            return;
        }

        Crashlytics.setString("errorMessage", String.format(message, args));
        Crashlytics.setString("lastRequests", getLastRequests());

        logRemoteException(throwable, summarized);
    }

    public static Void errorToVoid(Throwable throwable) {
        error(throwable);
        return null;
    }

    /**
     * Report an error.
     *
     * <p>This will go into crashlytics.</p>
     */
    public static void error(String message, Object... args) {
        error(new RuntimeException(
                String.format(message, args)
        ));
    }

    /**
     * Print and log an error to crashlytics.
     */
    public static void error(Throwable throwable) {
        final Throwable summarized = CrashReportingUtils.summarize(throwable);

        Timber.e(summarized, summarized.getMessage());
        logRemoteException(throwable, summarized);
    }

    /**
     * Print and log an error to crashlytics.
     */
    public static void error(Throwable throwable, String message, Object... args) {
        final Throwable summarized = CrashReportingUtils.summarize(throwable);

        // avoid crashing if there's no stacktrace (wtf, I know, right?)
        if (throwable.getStackTrace() == null) {
            Timber.e(message, args);

        } else {
            Timber.e(summarized, message, args);
        }

        logRemoteException(throwable, summarized, message, args);
    }

    /**
     * Report an error directly to Crashlytics and Timber, without any pre-processing. Only
     * for errors that can occuring during error-processing, to avoid re-entrance.
     */
    public static void rawError(Throwable error) {
        Timber.e(error);

        if (logToCrashlytics) {
            Crashlytics.logException(error);
        }
    }

    /**
     * Logs message to Timber and Crashlytics.
     *
     * @param message with string interpolation.
     * @param args    to be interpolated on the message.
     */
    public static void info(String message, Object... args) {
        Timber.i(message, args);

        if (!logToCrashlytics) {
            return;
        }

        Crashlytics.log(String.format(message, args));
    }

    public static void info(Throwable throwable, String message, Object... args) {
        Timber.i(throwable, message, args);
    }

    public static void debug(String message, Object... args) {
        Timber.d(message, args);
    }

    public static void debug(Throwable throwable, String message, Object... args) {
        Timber.d(throwable, message, args);
    }

    private static synchronized String getLastRequests() {
        final StringBuilder builder = new StringBuilder();
        for (String idempotencyKey : lastRequests) {
            final String uri = idempotencyKeysToUris.get(idempotencyKey);
            builder.append(uri);
            builder.append("\n");
        }
        return builder.toString();
    }

    /**
     * Save the URIs of the last requests made by the app to enhance error logs.
     */
    public static synchronized void saveRequestUri(String idempotencyKey, String uri) {
        final String previousValue = idempotencyKeysToUris.put(idempotencyKey, uri);
        if (previousValue == null) {
            lastRequests.add(0, idempotencyKey);
        }

        final int requestsSize = lastRequests.size();
        if (requestsSize > CRASHLYTICS_LAST_REQUESTS_AMOUNT) {
            final String removedIdempotencyKey = lastRequests.remove(requestsSize - 1);
            idempotencyKeysToUris.remove(removedIdempotencyKey);
        }
    }
}
