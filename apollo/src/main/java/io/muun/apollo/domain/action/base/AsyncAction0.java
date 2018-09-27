package io.muun.apollo.domain.action.base;

import rx.Observable;
import rx.functions.Func0;

public class AsyncAction0<R> extends AsyncAction<R> {

    private final Func0<Observable<R>> func;

    /**
     * Execution context for an async action.
     */
    AsyncAction0(String path,
                 Func0<Observable<R>> func,
                 Observable.Transformer<R, R> asyncExecutor) {

        super(path, asyncExecutor);
        this.func = func;
    }

    public void run() {
        super.run(func.call());
    }
}
