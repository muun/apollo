package io.muun.apollo.domain.action.base;

import rx.Observable;
import rx.functions.Func3;

public class AsyncAction3<T1, T2, T3, R> extends AsyncAction<R> {

    private final Func3<T1, T2, T3, Observable<R>> func;

    /**
     * Execution context for an async action.
     */
    AsyncAction3(String path,
                 Func3<T1, T2, T3, Observable<R>> func,
                 Observable.Transformer<R, R> asyncExecutor) {

        super(path, asyncExecutor);
        this.func = func;
    }

    public void run(T1 t1, T2 t2, T3 t3) {
        super.run(func.call(t1, t2, t3));
    }
}
