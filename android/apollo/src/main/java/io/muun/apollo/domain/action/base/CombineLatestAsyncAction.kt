package io.muun.apollo.domain.action.base

import rx.Observable

class CombineLatestAsyncAction<T, R>(
    private val actionStateObservableA: Observable<ActionState<T>>,
    private val actionStateObservableB: Observable<ActionState<R>>,
) {

    /**
     * Get the observable state of the action.
     */
    fun getState(): Observable<ActionState<Pair<T?, R?>>> =
            Observable.zip(
                actionStateObservableA,
                actionStateObservableB,
                this::merge
            )

    /**
     * Merge the ActionStates from 2 AsyncActions. Rules are:
     * - While any of them is LOADING -> returned ActionState is LOADING.
     * - When any of them is ERROR -> returned ActionState is ERROR.
     * - When BOTH of them are VALUE -> returned ActionState is Pair with both values.
     *
     * NOTE: for error handling take into account our rules. If you want/need something different
     * (e.g know if both actions errored or distinguish which one) you probably want to use another
     * approach and CombineLatestAsyncAction is not what you want.
     */
    private fun merge(
        a: ActionState<T>,
        b: ActionState<R>
    ): ActionState<Pair<T?, R?>> {

        return when {
            a.kind == ActionState.Kind.VALUE && b.kind == ActionState.Kind.VALUE ->
                ActionState.createValue(Pair(a.value, b.value))

            a.kind == ActionState.Kind.ERROR || b.kind == ActionState.Kind.ERROR ->
                ActionState.createError(a.error ?: b.error)

            a.kind == ActionState.Kind.EMPTY && b.kind == ActionState.Kind.EMPTY ->
                ActionState.createEmpty()

            else ->
                ActionState.createLoading()
        }
    }
}