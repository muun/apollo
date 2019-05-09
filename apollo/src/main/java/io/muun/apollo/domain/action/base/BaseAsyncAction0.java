package io.muun.apollo.domain.action.base;

import rx.Observable;

public abstract class BaseAsyncAction0<R> extends BaseAsyncAction<R> {

    public abstract Observable<R> action();

    public Observable<R> actionInBackground() {
        return super.actionInBackground(action());
    }

    public void run() {
        super.run(action());
    }
}
