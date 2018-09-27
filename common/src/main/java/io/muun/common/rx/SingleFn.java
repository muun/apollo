package io.muun.common.rx;

import io.muun.common.Optional;
import io.muun.common.api.error.ErrorCode;
import io.muun.common.exception.HttpException;

import rx.Single;
import rx.functions.Func1;

/**
 * Utility transformers for modifying singles.
 */
public final class SingleFn {

    private SingleFn() {
        throw new AssertionError();
    }

    /**
     * Composition transformer with the same functionality as a doOnSuccess, that accepts a function
     * that returns an single. The entire single will be consumed before proceeding with the
     * execution.
     */
    public static <T, U> Single.Transformer<T, T> flatDoOnSuccess(final Func1<T, Single<U>> func) {

        return new Single.Transformer<T, T>() {

            @Override
            public Single<T> call(Single<T> single) {

                return single.flatMap(

                        new Func1<T, Single<T>>() {

                            @Override
                            public Single<T> call(final T returnedItem) {

                                return func.call(returnedItem).map(

                                        new Func1<U, T>() {

                                            @Override
                                            public T call(U ignoredItem) {
                                                return returnedItem;
                                            }

                                        }

                                );

                            }
                        }

                );

            }

        };
    }

    /**
     * If the error emitted by the single is of type {ErrorT}, it gets replaced by the error
     * returned when calling replacer with the original error.
     */
    public static <T, ErrorT extends Throwable> Single.Transformer<T, T> replaceTypedError(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Throwable> replacer) {

        return onTypedErrorResumeNext(
                errorClass,
                new Func1<ErrorT, Single<T>>() {

                    @Override
                    public Single<T> call(ErrorT error) {
                        return Single.error(replacer.call(error));
                    }

                }
        );
    }

    /**
     * Like onErrorResumeNext, but only for errors of a specific type.
     */
    public static <T, ErrorT extends Throwable> Single.Transformer<T, T> onTypedErrorResumeNext(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Single<T>> resumeFunction) {

        return new Single.Transformer<T, T>() {

            @Override
            public Single<T> call(Single<T> single) {

                return single.onErrorResumeNext(

                        new Func1<Throwable, Single<? extends T>>() {

                            @Override
                            public Single<? extends T> call(Throwable error) {

                                final Optional<ErrorT> cause = getTypedCause(error, errorClass);

                                if (cause.isPresent()) {
                                    return resumeFunction.call(errorClass.cast(cause.get()));
                                } else {
                                    return Single.error(error);
                                }

                            }
                        }

                );

            }
        };
    }

    /**
     * Like onTypedErrorResumeNext, but specialized to HttpExceptions.
     */
    public static <T> Single.Transformer<T, T> onHttpExceptionResumeNext(
            final ErrorCode code,
            final Func1<HttpException, Single<T>> resumeFunction) {

        return new Single.Transformer<T, T>() {

            @Override
            public Single<T> call(Single<T> single) {

                return single.compose(onTypedErrorResumeNext(
                        HttpException.class,
                        new Func1<HttpException, Single<T>>() {

                            @Override
                            public Single<T> call(HttpException error) {

                                if (error.getErrorCode().equals(code)) {
                                    return resumeFunction.call(error);
                                }

                                return Single.error(error);
                            }
                        }
                ));
            }
        };
    }

    private static <T extends Throwable> Optional<T> getTypedCause(
            Throwable error,
            Class<T> errorClass) {

        for (Throwable cause = error;  cause != null; cause = cause.getCause()) {
            if (errorClass.isInstance(cause)) {
                return Optional.of(errorClass.cast(cause));
            }
        }

        return Optional.empty();
    }
}
