package io.muun.apollo.data.logging;

import io.muun.apollo.BuildConfig;
import io.muun.apollo.domain.model.User;
import io.muun.common.exception.HttpException;

import android.support.annotation.VisibleForTesting;
import com.crashlytics.android.Crashlytics;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.money.CurrencyQuery;
import javax.money.CurrencyQueryBuilder;
import javax.money.UnknownCurrencyException;
import javax.money.spi.Bootstrap;
import javax.money.spi.CurrencyProviderSpi;

import static java.util.Collections.synchronizedList;

public final class Logger {

    private static boolean logToCrashlytics = !BuildConfig.DEBUG;

    private static final int CRASHLYTICS_LAST_REQUESTS_AMOUNT = 5;
    private static final Map<String, String> idempotencyKeysToUris = new ConcurrentHashMap<>();
    private static final List<String> lastRequests = synchronizedList(new ArrayList<>());

    private Logger() {
        throw new AssertionError();
    }

    @VisibleForTesting
    public static void setLogToCrashlytics(boolean logToCrashlytics) {
        Logger.logToCrashlytics = logToCrashlytics;
    }

    public static void configureForUser(User user) {
        Crashlytics.setUserIdentifier(user.hid.toString());
        Crashlytics.setUserName(user.email);
    }

    private static void logRemoteException(Throwable throwable) {

        if (!logToCrashlytics) {
            return;
        }

        if (throwable instanceof HttpException) {

            final HttpException httpException = (HttpException) throwable;

            Crashlytics.setLong("requestId", httpException.getRequestId());
            Crashlytics.setInt("errorCode", httpException.getErrorCode().getCode());
            Crashlytics.setString("developerMessage", httpException.getDeveloperMessage());
        }

        if (throwable instanceof UnknownCurrencyException) {

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

        if (throwable.getStackTrace() == null) {
            Crashlytics.setString("Null stacktrace", throwable.getClass().getCanonicalName());
            // pretty please fill that stack trace
            throwable.fillInStackTrace();
        }

        Crashlytics.logException(throwable);
    }

    private static void logRemoteException(Throwable throwable, String message, Object... args) {
        Crashlytics.setString("errorMessage", String.format(message, args));
        Crashlytics.setString("lastRequests", getLastRequests());
        logRemoteException(throwable);
    }

    public static Void errorToVoid(Throwable throwable) {
        error(throwable);
        return null;
    }

    public static void error(String message, Object... args) {
        Timber.e(message, args);
    }

    public static void error(Throwable throwable) {
        Timber.e(throwable, throwable.getMessage());
        logRemoteException(throwable);
    }

    /**
     * Print and log an error to crashlytics.
     */
    public static void error(Throwable throwable, String message, Object... args) {

        // avoid crashing if there's no stacktrace (wtf, I know, right?)
        if (throwable.getStackTrace() == null) {
            Timber.e(message, args);
        } else {
            Timber.e(throwable, message, args);
        }

        logRemoteException(throwable, message, args);
    }

    /**
     * Logs message to Timber and Crashlytics.
     *
     * @param message with string interpolation.
     * @param args to be interpolated on the message.
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

    private static String getLastRequests() {
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
    public static void saveRequestUri(String idempotencyKey, String uri) {
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
