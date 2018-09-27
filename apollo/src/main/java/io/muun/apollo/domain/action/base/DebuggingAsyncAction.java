package io.muun.apollo.domain.action.base;

import io.muun.apollo.data.logging.Logger;

import rx.Observable;

import java.util.concurrent.atomic.AtomicInteger;

@Deprecated
public class DebuggingAsyncAction<T> extends AsyncAction<T> {

    // TODO: this code was rendered useless after the AsyncActionN refactor. Either integrate this
    // code directly on the AsyncAction (and activate it optionally), or inherit from each
    // AsyncActionN and override the same methods.

    private final AtomicInteger observersCount = new AtomicInteger(0);

    /**
     * Execution context for an async action.
     */
    public DebuggingAsyncAction(String path, Observable.Transformer<T, T> asyncExecutor) {
        super(path, asyncExecutor);
    }

    @Override
    public void run(Observable<T> action) {
        Logger.debug(isRunning() ? "%s: already running" : "%s: RUN", toString());
        super.run(action);
    }

    @Override
    public Observable<ActionState<T>> getState() {

        return super.getState()
                .doOnSubscribe(() -> {
                    if (observersCount.incrementAndGet() > 1) {
                        Logger.debug(
                                "%s: WARNING: more than one observer is subscribed to this action",
                                toString()
                        );
                    }
                })
                .doOnUnsubscribe(observersCount::decrementAndGet)
                .doOnNext(state -> {
                    switch (state.getKind()) {

                        case EMPTY:
                            Logger.debug("%s: EMPTY", toString());
                            break;

                        case LOADING:
                            Logger.debug("%s: LOADING", toString());
                            break;

                        case VALUE:
                            Logger.debug(
                                    "%s: VALUE = %s",
                                    toString(),
                                    state.getValue() != null ? state.getValue().toString() : null
                            );
                            break;

                        case ERROR:
                            Logger.debug(state.getError(), "%s: ERROR", toString());
                            break;

                        default:
                            break;
                    }
                });
    }

    @Override
    public void reset() {
        Logger.debug("%s: RESET", toString());
        super.reset();
    }
}
