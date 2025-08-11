package io.muun.apollo.data.os.execution;

import rx.Completable;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class ExecutionTransformerFactory {

    private final Observable.Transformer observableAsyncExecutor;

    private final Single.Transformer singleAsyncExecutor;

    private final Completable.Transformer completableAsyncExecutor;

    private final Observable.Transformer observableReverseAsyncExecutor;

    private final Scheduler backgroundScheduler;

    /**
     * Constructor.
     */
    @Inject
    public ExecutionTransformerFactory(
            Executor executor,
            @Named("mainThreadScheduler") Scheduler mainThreadScheduler
    ) {

        backgroundScheduler = Schedulers.from(executor);

        // don't remove the observable cast! javac seems to need it
        // noinspection rawtypes
        observableAsyncExecutor = observable -> ((Observable) observable)
                .subscribeOn(backgroundScheduler)
                .observeOn(mainThreadScheduler);

        // noinspection rawtypes
        singleAsyncExecutor = single -> ((Single) single)
                .subscribeOn(backgroundScheduler)
                .observeOn(mainThreadScheduler);

        completableAsyncExecutor = completable -> ((Completable) completable)
                .subscribeOn(backgroundScheduler)
                .observeOn(mainThreadScheduler);

        // noinspection rawtypes
        observableReverseAsyncExecutor = observable -> ((Observable) observable)
                .subscribeOn(mainThreadScheduler)
                .observeOn(backgroundScheduler);
    }

    /**
     * Return the Scheduler reserved for run background operations, used by async Transformers to
     * subscribe new Observables.
     */
    public Scheduler getBackgroundScheduler() {
        return backgroundScheduler;
    }

    /**
     * When composed with an observable, it makes the observable run on the injected {Executor}, and
     * the subscriber on the injected {Scheduler} (UI thread).
     *
     * @param <T> type of the original observable.
     * @return a {Transformer} to be applied with the {Observable.compose} operation.
     */
    @SuppressWarnings("unchecked")
    public <T> Observable.Transformer<T, T> getAsyncExecutor() {
        return observableAsyncExecutor;
    }

    /**
     * When composed with a single, it makes the single run on the injected {Executor}, and the
     * subscriber on the injected {Scheduler} (UI thread).
     *
     * @param <T> type of the original single.
     * @return a {Transformer} to be applied with the {Single.compose} operation.
     */
    @SuppressWarnings("unchecked")
    public <T> Single.Transformer<T, T> getSingleAsyncExecutor() {
        return singleAsyncExecutor;
    }

    /**
     * When composed with a completable, it makes the completable run on the injected {Executor},
     * and the subscriber on the injected {Scheduler} (UI thread).
     *
     * @return a {Transformer} to be applied with the {Completable.compose} operation.
     */
    public Completable.Transformer getCompletableAsyncExecutor() {
        return completableAsyncExecutor;
    }

    /**
     * When composed with an observable, it makes the observable run on the {Scheduler} (UI thread),
     * and the subscriber on the injected {Executor}.
     *
     * @param <T> type of the original observable.
     * @return a {Transformer} to be applied with the {Observable.compose} operation.
     */
    public <T> Observable.Transformer<T, T> getObservableReverseAsyncExecutor() {
        //noinspection unchecked
        return observableReverseAsyncExecutor;
    }
}
