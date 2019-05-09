package io.muun.apollo.domain.action.base;

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.action.UserActions;

import rx.Observable;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.functions.Func3;
import rx.functions.Func4;
import rx.functions.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AsyncActionStore {

    private final Observable.Transformer executor;

    private final Map<String, AsyncAction> map = new WeakHashMap<>();

    @Inject
    public AsyncActionStore(ExecutionTransformerFactory executionTransformerFactory) {
        executor = executionTransformerFactory.getAsyncExecutor();
    }

    public <R> AsyncAction0<R> get(String path, Func0<Observable<R>> func) {

        return get(path, func, AsyncAction0::new);
    }

    public <T, R> AsyncAction1<T, R> get(String path, Func1<T,Observable<R>> func) {

        return get(path, func, AsyncAction1::new);
    }

    public <T1, T2, R> AsyncAction2<T1, T2, R> get(String path, Func2<T1, T2, Observable<R>> func) {

        return get(path, func, AsyncAction2::new);
    }

    public <T1, T2, T3, R> AsyncAction3<T1, T2, T3, R> get(
            String path,
            Func3<T1, T2, T3, Observable<R>> func) {

        return get(path, func, AsyncAction3::new);
    }

    public <T1, T2, T3, T4, R> AsyncAction4<T1, T2, T3, T4, R> get(
            String path,
            Func4<T1, T2, T3, T4, Observable<R>> func) {

        return get(path, func, AsyncAction4::new);
    }

    /**
     * Get the async action for a given path.
     */
    @SuppressWarnings("unchecked")
    private <AsyncActionT extends AsyncAction, ObservableFuncT extends Function> AsyncActionT get(
            String path,
            ObservableFuncT func,
            Func3<String, ObservableFuncT, Observable.Transformer, AsyncActionT> constructor) {

        if (!map.containsKey(path)) {
            map.put(path, constructor.call(path, func, executor));
        }

        return (AsyncActionT) map.get(path);
    }

    public List<AsyncAction> getAll() {
        return new ArrayList<>(map.values());
    }

    private List<AsyncAction> getAllExcept(String excludedAction) {
        final List<AsyncAction> asyncActions = new ArrayList<>(map.size());

        for (String name : map.keySet()) {
            if (! excludedAction.equals(name)) {
                asyncActions.add(map.get(name));
            }
        }

        return asyncActions;
    }

    /**
     * Reset the state of all active async actions, emitting an EMPTY ActionState.
     */
    public void resetAll() {
        reset(getAll());
    }

    /**
     * Reset the state of all active async actions, except for the notifiyLogout action,
     * emitting an EMPTY ActionState.
     */
    public void resetAllExceptLogout() {
        reset(getAllExcept(UserActions.NOTIFY_LOGOUT_ACTION));
    }

    private void reset(List<AsyncAction> asyncActions) {
        for (AsyncAction asyncAction : asyncActions) {
            asyncAction.reset();
        }
    }
}
