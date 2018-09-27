package io.muun.apollo.domain.action.base;

import rx.Observable;
import rx.functions.Func4;

public class AsyncAction4<T1, T2, T3, T4, R> extends AsyncAction<R> {

    private final Func4<T1, T2, T3, T4, Observable<R>> func;

    /**
     * Execution context for an async action.
     */
    AsyncAction4(String path,
                 Func4<T1, T2, T3, T4, Observable<R>> func,
                 Observable.Transformer<R, R> asyncExecutor) {

        super(path, asyncExecutor);
        this.func = func;
    }

    public void run(T1 t1, T2 t2, T3 t3, T4 t4) {
        super.run(func.call(t1, t2, t3, t4));
    }
}
