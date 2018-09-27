package io.muun.apollo.data.os.execution;

import rx.Completable;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executor;

import javax.inject.Inject;
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
    public ExecutionTransformerFactory(Executor executor, Scheduler scheduler) {

        backgroundScheduler = Schedulers.from(executor);

        // don't remove the observable cast! javac seems to need it
        observableAsyncExecutor = observable -> ((Observable) observable)
                .subscribeOn(backgroundScheduler)
                .observeOn(scheduler);

        singleAsyncExecutor = single -> ((Single) single)
                .subscribeOn(backgroundScheduler)
                .observeOn(scheduler);

        completableAsyncExecutor = completable -> ((Completable) completable)
                .subscribeOn(backgroundScheduler)
                .observeOn(scheduler);

        observableReverseAsyncExecutor = observable -> ((Observable) observable)
                .subscribeOn(scheduler)
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
    @SuppressWarnings("unchecked")
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
        return observableReverseAsyncExecutor;
    }
}
