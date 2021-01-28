package io.muun.apollo;

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.action.base.AsyncAction0;
import io.muun.apollo.domain.action.base.AsyncAction1;
import io.muun.apollo.domain.action.base.AsyncAction2;
import io.muun.apollo.domain.action.base.AsyncAction3;
import io.muun.apollo.domain.action.base.AsyncAction4;
import io.muun.apollo.domain.action.base.AsyncActionStore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import rx.Observable;
import rx.Single;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.functions.Func3;
import rx.functions.Func4;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.spy;

/**
 * If you see some SIMILARITIES between our TestUtils classes (apollo, common, houston, etc...),
 * this is ON PURPOSE. There are a couple of reasons to have A LOT of duplicated code in this case:
 * - first, `loadJson` method uses `getResourceAsStream` which is sensitive to WHERE (which module)
 * it is being called. So in order to have test resources on each submodule this method should live
 * on the same module.
 * - second, you cannot export test classes as gradle does not build a jar for them. (Can be hacked,
 * but its ugly and painful. Please don't try at home).
 */
public final class TestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ExecutionTransformerFactory executionTransformerFactory =
            new ExecutionTransformerFactory(
                    Executors.newSingleThreadExecutor(),
                    Schedulers.newThread()
            );

    private static final AsyncActionStore asyncActionStore =
            new SpyAsyncActionStore(executionTransformerFactory);

    /**
     * Load a JSON file with a list of test cases.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> loadJson(String filename, TypeReference valueTypeRef) {

        final InputStream input = TestUtils.class.getResourceAsStream(filename);

        try {
            return (List<T>) OBJECT_MAPPER.readValue(input, valueTypeRef);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // ignored
            }
        }
    }

    /**
     * Fetch the first item from an observable.
     */
    public static <T> T fetchItemFromObservable(Observable<T> observable) {

        final TestSubscriber<T> subscriber = new TestSubscriber<>();

        observable.subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        subscriber.assertNoErrors();

        return subscriber.getOnNextEvents().get(0);
    }

    /**
     * Assert that an observable throws an error.
     */
    public static <T> void assertObservableThrows(Observable<T> observable) {

        assertObservableThrows(observable, Throwable.class);
    }

    /**
     * Assert that an observable throws a given error.
     */
    public static <T> void assertObservableThrows(Observable<T> observable,
                                                  Class<? extends Throwable> errorClass) {

        final TestSubscriber<T> subscriber = new TestSubscriber<>();

        observable.subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        subscriber.assertError(errorClass);
    }

    /**
     * Fetch the only item from a single.
     */
    public static <T> T fetchItemFromSingle(Single<T> single) {

        final TestSubscriber<T> subscriber = new TestSubscriber<>();

        single.subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        subscriber.assertNoErrors();

        return subscriber.getOnNextEvents().get(0);
    }

    /**
     * Assert that a single throws a given error.
     */
    public static <T> void assertSingleThrows(Single<T> single,
                                              Class<? extends Throwable> errorClass) {

        final TestSubscriber<T> subscriber = new TestSubscriber<>();

        single.subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        subscriber.assertError(errorClass);
    }

    public static ExecutionTransformerFactory getTransformerFactory() {
        return executionTransformerFactory;
    }

    public static AsyncActionStore getAsyncActionStore() {
        return asyncActionStore;
    }


    private static class SpyAsyncActionStore extends AsyncActionStore {

        public SpyAsyncActionStore(ExecutionTransformerFactory executionTransformerFactory) {
            super(executionTransformerFactory);
        }

        @Override
        public <R> AsyncAction0<R> get(String path,
                                       Func0<Observable<R>> f) {
            return spy(super.get(path, f));
        }

        @Override
        public <T, R> AsyncAction1<T, R> get(String path,
                                             Func1<T, Observable<R>> f) {
            return spy(super.get(path, f));
        }

        @Override
        public <T1, T2, R> AsyncAction2<T1, T2, R> get(String path,
                                                       Func2<T1, T2, Observable<R>> f) {
            return spy(super.get(path, f));
        }

        @Override
        public <T1, T2, T3, R> AsyncAction3<T1, T2, T3, R> get(String path,
                                                               Func3<T1, T2, T3, Observable<R>> f) {
            return spy(super.get(path, f));
        }

        @Override
        public <T1, T2, T3, T4, R> AsyncAction4<T1, T2, T3, T4, R> get(
                String path,
                Func4<T1, T2, T3, T4, Observable<R>> f) {

            return spy(super.get(path, f));
        }
    }

    private TestUtils() {
        throw new AssertionError();
    }

}
