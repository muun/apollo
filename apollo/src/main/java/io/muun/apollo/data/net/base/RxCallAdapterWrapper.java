package io.muun.apollo.data.net.base;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.errors.AmountTooSmallError;
import io.muun.apollo.domain.errors.CountryNotSupportedError;
import io.muun.apollo.domain.errors.DeprecatedClientVersionError;
import io.muun.apollo.domain.errors.EmailAreadyUsedError;
import io.muun.apollo.domain.errors.ExpiredSatelliteSession;
import io.muun.apollo.domain.errors.ExpiredSessionError;
import io.muun.apollo.domain.errors.ExpiredVerificationCodeError;
import io.muun.apollo.domain.errors.HardwareWalletAlreadyOwnedError;
import io.muun.apollo.domain.errors.IncorrectPasswordError;
import io.muun.apollo.domain.errors.InsufficientFundsError;
import io.muun.apollo.domain.errors.InvalidAddressError;
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError;
import io.muun.apollo.domain.errors.InvalidPhoneNumberError;
import io.muun.apollo.domain.errors.InvalidVerificationCodeError;
import io.muun.apollo.domain.errors.PhoneNumberAlreadyUsedError;
import io.muun.apollo.domain.errors.RevokedVerificationCodeError;
import io.muun.apollo.domain.errors.TooManyWrongVerificationCodesError;
import io.muun.common.Optional;
import io.muun.common.api.error.Error;
import io.muun.common.exception.HttpException;
import io.muun.common.rx.ExponentialBackoffRetry;
import io.muun.common.rx.ObservableFn;

import android.support.annotation.NonNull;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.CallAdapter;
import rx.Completable;
import rx.Observable;
import rx.Single;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.validation.constraints.NotNull;

public class RxCallAdapterWrapper<R> implements CallAdapter<R, Object> {

    // TODO move this to external configuration:
    private static final long CALL_TIMEOUT_SECONDS = 60;

    private static final Observable.Transformer HTTP_EXCEPTION_TRANSFORMER;
    private static final Observable.Transformer IO_EXCEPTION_TRANSFORMER;
    private static final Observable.Transformer SPECIAL_HTTP_EXCEPTIONS_TRANSFORMER;

    static {

        HTTP_EXCEPTION_TRANSFORMER = ObservableFn.replaceTypedError(
                retrofit2.HttpException.class,
                error -> {
                    final Error apiError = deserializeApiError(error).orElse(null);

                    if (apiError != null && error.code() < 500) {
                        // We managed to parse a valid APIError that corresponds to a client-side
                        // failure. Upper layers may want to react appropriately:
                        return new HttpException(apiError);

                    } else {
                        // Server-side failures are not very useful to differentiate in Apollo. We
                        // emit a single class with a predefined message:
                        return new ServerFailureException(error);
                    }
                }
        );

        IO_EXCEPTION_TRANSFORMER = ObservableFn.replaceTypedError(
                IOException.class,
                NetworkException::new
        );

        SPECIAL_HTTP_EXCEPTIONS_TRANSFORMER = ObservableFn.replaceTypedError(
                HttpException.class,
                error -> {
                    switch (error.getErrorCode()) {
                        case DEPRECATED_CLIENT_VERSION:
                            return new DeprecatedClientVersionError();

                        case EXPIRED_SESSION:
                            return new ExpiredSessionError();

                        case INVALID_PHONE_NUMBER:
                            return new InvalidPhoneNumberError();

                        case COUNTRY_NOT_SUPPORTED:
                            return new CountryNotSupportedError();

                        case INVALID_VERIFICATION_CODE:
                            return new InvalidVerificationCodeError();

                        case REVOKED_VERIFICATION_CODE:
                            return new RevokedVerificationCodeError();

                        case EXPIRED_VERIFICATION_CODE:
                            return new ExpiredVerificationCodeError();

                        case TOO_MANY_WRONG_VERIFICATION_CODES:
                            return new TooManyWrongVerificationCodesError(); // that's a mouthful :)

                        case PHONE_NUMBER_ALREADY_USED:
                            return new PhoneNumberAlreadyUsedError();

                        case EMAIL_ALREADY_USED:
                            return new EmailAreadyUsedError();

                        case INSUFFICIENT_CLIENT_FUNDS:
                            return new InsufficientFundsError();

                        case INVALID_ADDRESS:
                            return new InvalidAddressError();

                        case INVALID_PASSWORD:
                            return new IncorrectPasswordError();

                        case INVALID_CHALLENGE_SIGNATURE:
                            return new InvalidChallengeSignatureError();

                        case AMOUNT_SMALLER_THAN_DUST:
                            return new AmountTooSmallError();

                        case HARDWARE_WALLET_ALREADY_OWNED:
                            return new HardwareWalletAlreadyOwnedError();

                        case EXPIRED_SATELLITE_SESSION:
                            return new ExpiredSatelliteSession();

                        default:
                            return error;
                    }
                }
        );
    }

    private static Optional<Error> deserializeApiError(retrofit2.HttpException error) {

        try {
            final ResponseBody responseBody = error.response().errorBody();

            if (responseBody == null) {
                return Optional.empty();
            }

            final Error apiError = SerializationUtils.deserializeJson(
                    Error.class,
                    responseBody.string()
            );

            return Optional.of(apiError);

        } catch (IOException | IllegalArgumentException ignored) {
            // This HTTP response doesn't seem generated by Houston. Perhaps a proxy
            // or reverse-proxy failed to deliver the request.
            Logger.error(error);
            return Optional.empty();
        }
    }

    private final CallAdapter<R, ?> wrapped;
    private final Map<Long, String> idempotencyKeyForThreadHack;
    private final boolean isSingle;
    private final boolean isCompletable;

    /**
     * Constructor.
     */
    public RxCallAdapterWrapper(
            CallAdapter<R, ?> wrapped,
            Map<Long, String> idempotencyKeyForThreadHack,
            boolean isSingle,
            boolean isCompletable) {

        this.wrapped = wrapped;
        this.idempotencyKeyForThreadHack = idempotencyKeyForThreadHack;
        this.isSingle = isSingle;
        this.isCompletable = isCompletable;
    }

    @Override
    public Type responseType() {
        return wrapped.responseType();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object adapt(@NonNull Call<R> call) {

        final String idempotencyKey = UUID.randomUUID().toString();

        // In order to use the same idempotency key for all the retries in the same request chain,
        // we need to relate all the calls in the idempotency key header interceptor. This is
        // difficult because the retry logic is done at the call adapter level (retrofit), and the
        // idempotency key header is added at the interceptor level (okhttp).
        //
        // Since retrofit doesn't expose any hook into the request creation process, we can't send
        // any information from the call adapter to the interceptor through the request object, as
        // recommended here:
        // https://publicobject.com/2016/01/17/sneaking-data-into-an-okhttp-interceptor/
        //
        // To circumvent this limitation, we make an awful hack: each time we re-subscribe to the
        // observer (ie. on each retry), we keep track of the thread id and annotate it with the
        // idempotency key. Since we know that the interceptor will be run in the same thread, we
        // can ask for the key annotated for the thread in which the interceptor is being run.

        final ExponentialBackoffRetry networkExceptionRetryPolicy =
                new ExponentialBackoffRetry(1, 7, NetworkException.class);

        final ExponentialBackoffRetry serverExceptionRetryPolicy =
                new ExponentialBackoffRetry(2, 3, ServerFailureException.class);

        final Observable<?> result = getRequestObservable(call)
                .doOnSubscribe(() -> idempotencyKeyForThreadHack.put(
                        Thread.currentThread().getId(),
                        idempotencyKey
                ))
                .compose(HTTP_EXCEPTION_TRANSFORMER)
                .compose(SPECIAL_HTTP_EXCEPTIONS_TRANSFORMER)
                .compose(IO_EXCEPTION_TRANSFORMER)
                .retryWhen(networkExceptionRetryPolicy)
                .retryWhen(serverExceptionRetryPolicy)
                .timeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // Avoid TimeoutException from "eating" information about an underlying error
                .compose(ObservableFn.replaceTypedError(
                        TimeoutException.class,
                        timeoutException -> networkExceptionRetryPolicy.getLastError()
                                .ifEmptyGet(serverExceptionRetryPolicy::getLastError)
                                .orElse(new NetworkException(timeoutException))
                ));

        return getResponseObservable(result);
    }

    @SuppressWarnings("unchecked")
    private Observable<?> getRequestObservable(@NotNull Call<R> call) {

        if (isSingle) {
            return ((CallAdapter<R, Single>) wrapped).adapt(call).toObservable();
        }

        if (isCompletable) {
            return ((CallAdapter<R, Completable>) wrapped).adapt(call).toObservable();
        }

        return ((CallAdapter<R, Observable>) wrapped).adapt(call);
    }

    private Object getResponseObservable(Observable<?> result) {

        if (isSingle) {
            return result.toSingle();
        }

        if (isCompletable) {
            return result.toCompletable();
        }

        return result;
    }
}
