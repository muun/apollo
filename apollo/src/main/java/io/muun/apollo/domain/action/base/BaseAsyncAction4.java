package io.muun.apollo.domain.action.base;

import rx.Observable;

public abstract class BaseAsyncAction4<T1, T2, T3, T4, R> extends BaseAsyncAction<R> {

    public abstract Observable<R> action(T1 t1, T2 t2, T3 t3, T4 t4);

    public void run(T1 t1, T2 t2, T3 t3, T4 t4) {
        super.run(action(t1, t2, t3, t4));
    }
}
