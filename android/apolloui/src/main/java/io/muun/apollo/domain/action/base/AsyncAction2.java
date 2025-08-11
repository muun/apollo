package io.muun.apollo.domain.action.base;

import rx.Observable;
import rx.functions.Func2;

public class AsyncAction2<T1, T2, R> extends AsyncAction<R> {

    private final Func2<T1, T2, Observable<R>> func;

    /**
     * Execution context for an async action.
     */
    AsyncAction2(String path,
                 Func2<T1, T2, Observable<R>> func,
                 Observable.Transformer<R, R> asyncExecutor) {

        super(path, asyncExecutor);
        this.func = func;
    }

    public void run(T1 t1, T2 t2) {
        super.run(func.call(t1, t2));
    }
}
