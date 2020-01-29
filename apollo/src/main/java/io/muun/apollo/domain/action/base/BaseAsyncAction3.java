package io.muun.apollo.domain.action.base;

import rx.Observable;

public abstract class BaseAsyncAction3<T1, T2, T3, R> extends BaseAsyncAction<R> {

    public abstract Observable<R> action(T1 t1, T2 t2, T3 t3);

    public void run(T1 t1, T2 t2, T3 t3) {
        super.run(action(t1, t2, t3));
    }

    public R runNow(T1 t1, T2 t2, T3 t3) {
        return super.runNow(action(t1, t2, t3));
    }
}
