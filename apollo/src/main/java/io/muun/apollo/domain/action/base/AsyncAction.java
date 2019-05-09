package io.muun.apollo.domain.action.base;

import io.muun.apollo.data.logging.CrashReportingUtils;
import io.muun.apollo.data.logging.Logger;

import android.util.Log;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.Exceptions;
import rx.subjects.BehaviorSubject;

public class AsyncAction<T> {

    private final BehaviorSubject<ActionState<T>> subject =
            BehaviorSubject.create(ActionState.createEmpty());

    private final String name;

    private final Observable.Transformer<T, T> asyncExecutor;

    private Subscription subscription;

    /**
     * Execution context for an async action.
     */
    public AsyncAction(String name, Observable.Transformer<T, T> asyncExecutor) {

        this.name = name;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Execute an observable in the context of this async action.
     */
    protected synchronized void run(Observable<T> action) {

        if (isRunning()) {
            this.log("Already running");
            return;
        }

        subscription = action
                .compose(asyncExecutor)
                .doOnSubscribe(this::setLoading)
                .subscribe(this::setValue, this::setError);
    }

    /**
     * Get the observable state of the action.
     */
    public Observable<ActionState<T>> getState() {

        return subject
                .lift(subscriber -> new Subscriber<ActionState<T>>(subscriber) {

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
                    public void onNext(ActionState<T> state) {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onNext(state);
                            if (state.isValue() || state.isError()) {
                                if (! isEmpty()) {
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
    public Observable<T> getResult() {

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
    public boolean isRunning() {

        return subscription != null && !subscription.isUnsubscribed();
    }

    private boolean isEmpty() {
        return subject.getValue().isEmpty();
    }

    private void setEmpty() {
        log("EMPTY");
        subject.onNext(ActionState.createEmpty());
    }

    private void setLoading() {
        log("LOADING");
        subject.onNext(ActionState.createLoading());
    }

    private void setValue(T value) {
        log("VALUE " + value);
        subject.onNext(ActionState.createValue(value));
    }

    private void setError(Throwable error) {
        log("ERROR " + Log.getStackTraceString(CrashReportingUtils.summarize(error)));
        subject.onNext(ActionState.createError(error));
    }

    private void log(String what) {
        Logger.debug("AsyncAction " + name + " " + what);
    }

    private void unsubscribe() {

        if (isRunning()) {
            subscription.unsubscribe();
        }
    }

    @Override
    public String toString() {

        return String.format("AsyncAction[%s]", name);
    }
}
