package io.muun.apollo.domain.action.base;

import rx.Observable;

public abstract class BaseAsyncAction2<T1, T2, R> extends BaseAsyncAction<R> {

    public abstract Observable<R> action(T1 t1, T2 t2);

    public void run(T1 t1, T2 t2) {
        super.run(action(t1, t2));
    }
}
