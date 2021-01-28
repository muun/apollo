package io.muun.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.Networks;
import rx.Observable;
import rx.Single;
import rx.observers.TestSubscriber;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * If you see some SIMILARITIES between our TestUtils classes (apollo, common, houston, etc...),
 * this is ON PURPOSE. There are a couple of reasons to have A LOT of duplicated code in this case:
 * - first, `loadJson` method uses `getResourceAsStream` which is sensitive to WHERE (which module)
 * it is being called. So in order to have test resources on each submodule this method should live
 * on the same module.
 * - second, you cannot export test classes as gradle does not build a jar for them. (Can be hacked,
 * but its ugly and painful. Please don't try at home).
 */
public class TestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void setBitcoinjContext(final NetworkParameters networkParameters) {
        final Context ctx = new Context(networkParameters);
        Context.propagate(ctx);
        Networks.register(networkParameters);
    }

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

    private TestUtils() {
        throw new AssertionError();
    }
}
