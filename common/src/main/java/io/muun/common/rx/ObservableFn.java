package io.muun.common.rx;

import io.muun.common.Optional;
import io.muun.common.api.error.ErrorCode;
import io.muun.common.exception.HttpException;

import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

/**
 * Utility transformers for modifying observables.
 */
public final class ObservableFn {

    private ObservableFn() {
        throw new AssertionError();
    }

    /**
     * Composition transformer with the same functionality as a doOnNext, that accepts a function
     * that returns an observable. The entire observable will be consumed before proceeding with the
     * execution.
     */
    public static <T, U> Transformer<T, T> flatDoOnNext(final Func1<T, Observable<U>> func) {

        return new Transformer<T, T>() {

            @Override
            public Observable<T> call(Observable<T> observable) {

                return observable.flatMap(

                        new Func1<T, Observable<T>>() {

                            @Override
                            public Observable<T> call(final T returnedItem) {

                                return func.call(returnedItem).last().map(

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
     * If the error emitted by the observable is of type {ErrorT}, it gets replaced by the error
     * returned when calling replacer with the original error.
     */
    public static <T, ErrorT extends Throwable> Transformer<T, T> replaceTypedError(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Throwable> replacer) {

        return onTypedErrorResumeNext(
                errorClass,
                new Func1<ErrorT, Observable<T>>() {

                    @Override
                    public Observable<T> call(ErrorT error) {
                        return Observable.error(replacer.call(error));
                    }

                }
        );
    }

    /**
     * Consume and ignore errors of a given type.
     */
    public static <T, ErrorT extends Throwable> Transformer<T, T> ignoreTypedError(
            final Class<ErrorT> errorClass) {

        return onTypedErrorResumeNext(
                errorClass,
                new Func1<ErrorT, Observable<T>>() {

                    @Override
                    public Observable<T> call(ErrorT error) {
                        return Observable.empty();
                    }

                }
        );
    }

    /**
     * Like onErrorResumeNext, but only for errors of a specific type.
     */
    public static <T, ErrorT extends Throwable> Transformer<T, T> onTypedErrorResumeNext(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Observable<T>> resumeFunction) {

        return new Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> observable) {

                return observable.onErrorResumeNext(

                        new Func1<Throwable, Observable<? extends T>>() {

                            @Override
                            public Observable<? extends T> call(Throwable error) {

                                final Optional<ErrorT> cause = getTypedCause(error, errorClass);

                                if (cause.isPresent()) {
                                    return resumeFunction.call(errorClass.cast(cause.get()));

                                } else {
                                    return Observable.error(error);
                                }

                            }
                        }

                );

            }
        };
    }

    /**
     * Consume and ignore HttpExceptions of a given type.
     */
    public static <T> Transformer<T, T> ignoreHttpException(final ErrorCode code) {

        return onHttpExceptionResumeNext(
                code,
                new Func1<HttpException, Observable<T>>() {

                    @Override
                    public Observable<T> call(HttpException error) {
                        return Observable.empty();
                    }

                }
        );
    }

    /**
     * If the error emitted by the single is of type HttpException and has a specific code, it gets
     * replaced by the error returned when calling replacer with the original error.
     */
    public static <T> Transformer<T, T> replaceHttpException(
            final ErrorCode code,
            final Func1<HttpException, Throwable> replacer) {

        return onHttpExceptionResumeNext(
                code,
                new Func1<HttpException, Observable<T>>() {

                    @Override
                    public Observable<T> call(HttpException error) {
                        return Observable.error(replacer.call(error));
                    }

                }
        );
    }

    /**
     * Like onTypedErrorResumeNext, but specialized to HttpExceptions.
     */
    public static <T> Transformer<T, T> onHttpExceptionResumeNext(
            final ErrorCode code,
            final Func1<HttpException, Observable<T>> resumeFunction) {

        return new Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> observable) {

                return observable.onErrorResumeNext(

                        new Func1<Throwable, Observable<? extends T>>() {

                            @Override
                            public Observable<? extends T> call(Throwable error) {

                                final Optional<HttpException> cause = getTypedCause(
                                        error,
                                        HttpException.class
                                );

                                if (cause.isPresent() && code.equals(cause.get().getErrorCode())) {
                                    return resumeFunction.call(cause.get());

                                } else {
                                    return Observable.error(error);
                                }

                            }
                        }

                );

            }
        };
    }

    private static <T extends Throwable> Optional<T> getTypedCause(
            Throwable error,
            Class<T> errorClass) {

        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (errorClass.isInstance(cause)) {
                return Optional.of(errorClass.cast(cause));
            }
        }

        return Optional.empty();
    }
}
