package io.muun.apollo.domain.action.base;

import rx.Observable;

public abstract class BaseAsyncAction1<T, R> extends BaseAsyncAction<R> {

    public abstract Observable<R> action(T t);

    public Observable<R> actionInBackground(T t) {
        return super.actionInBackground(action(t));
    }

    public void run(T t) {
        super.run(action(t));
    }
}
