package io.muun.apollo.domain.action.base;

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.Exceptions;
import rx.subjects.BehaviorSubject;
import timber.log.Timber;

import javax.inject.Inject;

public class BaseAsyncAction<ReturnT> {

    @Inject
    ExecutionTransformerFactory executionTransformerFactory;

    private final BehaviorSubject<ActionState<ReturnT>> subject =
            BehaviorSubject.create(ActionState.createEmpty());

    private Subscription subscription;

    /**
     * Get the action running in a background thread and posting results to the UI thread.
     */
    private Observable<ReturnT> actionInBackground(Observable<ReturnT> action) {

        return action.compose(executionTransformerFactory.getAsyncExecutor());
    }

    /**
     * Execute an observable in the context of this async action.
     */
    protected synchronized void run(Observable<ReturnT> action) {

        if (isRunning()) {
            this.log("Already running");
            return;
        }

        subscription = actionInBackground(action)
                .doOnSubscribe(this::setLoading)
                .subscribe(this::setValue, this::setError);
    }

    /**
     * Get the observable state of the action.
     */
    public Observable<ActionState<ReturnT>> getState() {

        return subject
                .lift(subscriber -> new Subscriber<ActionState<ReturnT>>(subscriber) {

                    @Override
                    public void onCompleted() {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onCompleted();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onError(error);
                        }
                    }

                    @Override
                    public void onNext(ActionState<ReturnT> state) {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onNext(state);
                            if (state.isValue() || state.isError()) {
                                if (! subject.getValue().isEmpty()) {
                                    setEmpty();
                                }
                            }
                        }
                    }
                });
    }

    /**
     * Get the observable result of the action.
     *
     * <p>Be careful with the returned Observable, since it breaks the observable contract: the
     * source may continue emitting events after an error.
     */
    public Observable<ReturnT> getResult() {

        return getState()
                .filter(state -> {
                    switch (state.getKind()) {
                        case EMPTY:
                        case LOADING:
                            return false;
                        case VALUE:
                            return true;
                        case ERROR:
                            throw Exceptions.propagate(state.getError());
                        default:
                            return false;
                    }
                })
                .map(ActionState::getValue);
    }

    /**
     * Abort the action if running, and return the action state to EMPTY.
     */
    public void reset() {

        unsubscribe();
        setEmpty();
    }

    /**
     * Whether the action is running.
     */
    public synchronized boolean isRunning() {

        return subscription != null && !subscription.isUnsubscribed();
    }

    private synchronized void unsubscribe() {

        if (isRunning()) {
            subscription.unsubscribe();
        }
    }

    private void setEmpty() {
        Timber.d(getName() + ": EMPTY");
        subject.onNext(ActionState.createEmpty());
    }

    private void setLoading() {
        Timber.d(getName() + ": LOADING");
        subject.onNext(ActionState.createLoading());
    }

    private void setValue(ReturnT value) {
        Timber.d(getName() + ": VALUE " + value);
        subject.onNext(ActionState.createValue(value));
    }

    private void setError(Throwable error) {
        Timber.d(getName() + ": ERROR " + error);
        Timber.e(error);
        subject.onNext(ActionState.createError(error));
    }

    private String getName() {
        return this.getClass().getSimpleName();
    }

    private void log(String what) {
        Timber.d("[AsyncAction " + this.getClass().getSimpleName() + "] " + what);
    }
}
