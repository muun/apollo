package io.muun.apollo.data.net.base;

import androidx.annotation.NonNull;
import retrofit2.CallAdapter;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import rx.Completable;
import rx.Observable;
import rx.Single;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CallAdapterFactory extends CallAdapter.Factory {

    private final RxJavaCallAdapterFactory original;

    private final Map<Long, String> idempotencyKeyForThreadHack;

    @Inject
    public CallAdapterFactory() {
        original = RxJavaCallAdapterFactory.create();
        idempotencyKeyForThreadHack = new HashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public CallAdapter<?, ?> get(@NonNull Type returnType,
                                 @NonNull Annotation[] annotations,
                                 @NonNull Retrofit retrofit) {

        final Class<?> rawType = getRawType(returnType);

        final boolean isObservable = rawType == Observable.class;
        final boolean isSingle = rawType == Single.class;
        final boolean isCompletable = rawType == Completable.class;

        if (!isObservable && !isSingle && !isCompletable) {
            return null;
        }

        return new RxCallAdapterWrapper(
                original.get(returnType, annotations, retrofit),
                idempotencyKeyForThreadHack,
                isSingle,
                isCompletable
        );
    }

    public String getIdempotencyKey() {
        return idempotencyKeyForThreadHack.get(Thread.currentThread().getId());
    }
}
