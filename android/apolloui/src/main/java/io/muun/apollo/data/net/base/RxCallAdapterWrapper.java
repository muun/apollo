package io.muun.apollo.data.net.base;

import io.muun.apollo.data.logging.LoggingRequestTracker;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.errors.DeprecatedClientVersionError;
import io.muun.apollo.domain.errors.ExpiredActionLinkError;
import io.muun.apollo.domain.errors.ExpiredSessionError;
import io.muun.apollo.domain.errors.InvalidActionLinkError;
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError;
import io.muun.apollo.domain.errors.InvalidJsonError;
import io.muun.apollo.domain.errors.TooManyRequestsError;
import io.muun.apollo.domain.errors.newop.AmountTooSmallError;
import io.muun.apollo.domain.errors.newop.ExchangeRateWindowTooOldError;
import io.muun.apollo.domain.errors.newop.InsufficientFundsError;
import io.muun.apollo.domain.errors.newop.InvalidAddressError;
import io.muun.apollo.domain.errors.p2p.CountryNotSupportedError;
import io.muun.apollo.domain.errors.p2p.ExpiredVerificationCodeError;
import io.muun.apollo.domain.errors.p2p.InvalidPhoneNumberError;
import io.muun.apollo.domain.errors.p2p.InvalidVerificationCodeError;
import io.muun.apollo.domain.errors.p2p.PhoneNumberAlreadyUsedError;
import io.muun.apollo.domain.errors.p2p.RevokedVerificationCodeError;
import io.muun.apollo.domain.errors.p2p.TooManyWrongVerificationCodesError;
import io.muun.apollo.domain.errors.passwd.EmailAlreadyUsedError;
import io.muun.apollo.domain.errors.passwd.EmailNotRegisteredError;
import io.muun.apollo.domain.errors.passwd.IncorrectPasswordError;
import io.muun.apollo.domain.errors.rc.CredentialsDontMatchError;
import io.muun.apollo.domain.errors.rc.InvalidRecoveryCodeV2Error;
import io.muun.apollo.domain.errors.rc.StaleChallengeKeyError;
import io.muun.common.Optional;
import io.muun.common.api.error.Error;
import io.muun.common.api.error.ErrorCode;
import io.muun.common.exception.HttpException;
import io.muun.common.net.HeaderUtils;
import io.muun.common.net.NetworkRetry;
import io.muun.common.net.ServerRetry;
import io.muun.common.rx.ObservableFn;

import androidx.annotation.NonNull;
import com.fasterxml.jackson.core.JsonProcessingException;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Invocation;
import rx.Completable;
import rx.Observable;
import rx.Observable.Transformer;
import rx.Single;
import timber.log.Timber;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import javax.validation.constraints.NotNull;

public class RxCallAdapterWrapper<R> implements CallAdapter<R, Object> {

    private static final Transformer HTTP_EXCEPTION_TRANSFORMER;
    private static final Transformer JSON_DESERIALIZATION_TRANSFORMER;
    private static final Transformer IO_EXCEPTION_TRANSFORMER;
    private static final Transformer SPECIAL_HTTP_EXCEPTIONS_TRANSFORMER;

    static {

        // We need a reference to ErrorCode here so Java classloader loads the class and executes
        // its static initializer to load the errorCodes map. Somehow this wasn't necessary before,
        // but after c38e9c0 we need to manually do this.
        final ErrorCode ignoredSoWeCanLoadErrorCodesMap = ErrorCode.EMAIL_ALREADY_SET_UP;

        HTTP_EXCEPTION_TRANSFORMER = ObservableFn.replaceTypedError(
                retrofit2.HttpException.class,
                error -> {
                    final Error apiError = deserializeApiError(error).orElse(null);

                    final String idempotencyKey = error.response().raw().request()
                            .header(HeaderUtils.IDEMPOTENCY_KEY);

                    if (idempotencyKey != null) {
                        LoggingRequestTracker.INSTANCE
                                .reportRecentErrorResponse(idempotencyKey, apiError);
                    }

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

        // Jackson's Json deserialization errors are all IOExceptions, but don't want to retry
        // since they won't succeed unless we fix the (code) problem. Also, let's throw a specific
        // error so we can handle it separately.
        JSON_DESERIALIZATION_TRANSFORMER = ObservableFn.replaceTypedError(
                JsonProcessingException.class,
                InvalidJsonError::new
        );

        // Use replaceTypedErrorExact to avoid replacing Jackson errors (subclasses of IOException)
        // TODO find a better way to deal with this?
        IO_EXCEPTION_TRANSFORMER = ObservableFn.replaceTypedErrorExact(
                IOException.class,
                NetworkException::new
        );

        SPECIAL_HTTP_EXCEPTIONS_TRANSFORMER = ObservableFn.replaceTypedError(
                HttpException.class,
                error -> {
                    switch ((ErrorCode) error.getErrorCode()) {
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
                            return new EmailAlreadyUsedError();

                        case EMAIL_NOT_REGISTERED:
                            return new EmailNotRegisteredError();

                        case INSUFFICIENT_CLIENT_FUNDS:
                            return new InsufficientFundsError();

                        case INVALID_ADDRESS:
                            return new InvalidAddressError();

                        case INVALID_PASSWORD:
                            return new IncorrectPasswordError();

                        case INVALID_CHALLENGE_SIGNATURE:
                            return new InvalidChallengeSignatureError();

                        case AMOUNT_SMALLER_THAN_DUST:
                            return new AmountTooSmallError(-1); // symbolic, this shouldn't happen

                        case EXCHANGE_RATE_WINDOW_TOO_OLD:
                            return new ExchangeRateWindowTooOldError();

                        case EMAIL_LINK_EXPIRED:
                            return new ExpiredActionLinkError();

                        case EMAIL_LINK_INVALID:
                            return new InvalidActionLinkError();

                        case RECOVERY_CODE_V2_NOT_SET_UP:
                            return new InvalidRecoveryCodeV2Error();

                        case HTTP_TOO_MANY_REQUESTS:
                            return new TooManyRequestsError();

                        case STALE_CHALLENGE_KEY:
                            return new StaleChallengeKeyError();

                        case CREDENTIALS_DONT_MATCH:
                            return new CredentialsDontMatchError();

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
            Timber.e(error);
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

        final String url = call.request().url().toString();
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

        final NetworkRetry networkRetryConfig = getAnnotation(call, NetworkRetry.class);
        final ServerRetry serverRetryConfig = getAnnotation(call, ServerRetry.class);
        final HttpRetryTransformer<R> retryTransformer = new HttpRetryTransformer<>(
                networkRetryConfig,
                serverRetryConfig
        );

        final Transformer<R,R> attachUrlTransformer = ObservableFn.replaceTypedError(
                NetworkException.class,
                original -> new NetworkException(url, original)
        );

        final Observable<R> result = getRequestObservable(call)
                .doOnSubscribe(() -> idempotencyKeyForThreadHack.put(
                        Thread.currentThread().getId(),
                        idempotencyKey
                ))
                .compose(HTTP_EXCEPTION_TRANSFORMER)
                .compose(SPECIAL_HTTP_EXCEPTIONS_TRANSFORMER)
                .compose(JSON_DESERIALIZATION_TRANSFORMER)
                .compose(IO_EXCEPTION_TRANSFORMER)
                .compose(retryTransformer)
                .compose(attachUrlTransformer)
                .doOnNext(ignored -> reportSuccessResponse(idempotencyKey));

        return getResponseObservable(result);
    }

    private void reportSuccessResponse(String idempotencyKey) {
        LoggingRequestTracker.INSTANCE.reportRecentSuccessResponse(idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private Observable<?> getRequestObservable(@NotNull Call<R> call) {

        if (isSingle) {
            return ((CallAdapter<R, Single<R>>) wrapped).adapt(call).toObservable();
        }

        if (isCompletable) {
            return ((CallAdapter<R, Completable>) wrapped).adapt(call).toObservable();
        }

        return ((CallAdapter<R, Observable<R>>) wrapped).adapt(call);
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

    private <T extends Annotation> T getAnnotation(final Call<R> call, final Class<T> aClass) {

        final Invocation invocation = call.request().tag(Invocation.class);
        if (invocation != null) {
            return invocation.method().getAnnotation(aClass);
        }

        return null;
    }
}
