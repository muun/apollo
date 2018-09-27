package io.muun.apollo.domain.action.base;

import rx.Observable;
import rx.functions.Func1;

public class AsyncAction1<T, R> extends AsyncAction<R> {

    private final Func1<T, Observable<R>> func;

    /**
     * Execution context for an async action.
     */
    AsyncAction1(String path,
                 Func1<T, Observable<R>> func,
                 Observable.Transformer<R, R> asyncExecutor) {

        super(path, asyncExecutor);
        this.func = func;
    }

    public void run(T t) {
        super.run(func.call(t));
    }
}
