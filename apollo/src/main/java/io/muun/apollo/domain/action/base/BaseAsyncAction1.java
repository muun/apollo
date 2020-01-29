package io.muun.apollo.domain.action.base;

import rx.Observable;

public abstract class BaseAsyncAction1<T, R> extends BaseAsyncAction<R> {

    public abstract Observable<R> action(T t);

    public void run(T t) {
        super.run(action(t));
    }

    public R runNow(T t) {
        return super.runNow(action(t));
    }
}
