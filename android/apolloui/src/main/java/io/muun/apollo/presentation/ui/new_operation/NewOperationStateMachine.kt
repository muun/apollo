package io.muun.apollo.presentation.ui.new_operation

import io.muun.apollo.domain.errors.newop.NewOpStateError
import newop.*
import rx.Observable
import rx.subjects.BehaviorSubject

class NewOperationStateMachine {

    private val subject: BehaviorSubject <State> = BehaviorSubject.create()
    private val listener: Listener = Listener(subject)

    init {
        subject.onNext(Newop.newOperationFlow(listener))
    }

    fun asObservable(): Observable<State> {
        return subject.asObservable()
    }

    fun value(): State? {
        return subject.value
    }

    inline fun <reified T : State> withState(f: (state: T) -> Unit) {
        val value = value()
        if (value !is T) {
            val actual = if (value != null) value::class.java else null
            throw NewOpStateError(actual, T::class.java)
        }

        f(value)
    }
}

class Listener(private val subject: BehaviorSubject<State>): TransitionListener {

    override fun onResolve(nextState: ResolveState) {
        subject.onNext(nextState)
    }

    override fun onEnterAmount(nextState: EnterAmountState) {
        subject.onNext(nextState)
    }

    override fun onEnterDescription(nextState: EnterDescriptionState) {
        subject.onNext(nextState)
    }

    override fun onConfirm(nextState: ConfirmState) {
        subject.onNext(nextState)
    }

    override fun onConfirmLightning(nextState: ConfirmLightningState?) {
        subject.onNext(nextState)
    }

    override fun onEditFee(nextState: EditFeeState) {
        subject.onNext(nextState)
    }

    override fun onError(nextState: ErrorState) {
        subject.onNext(nextState)
    }

    override fun onAbort(abortState: AbortState) {
        subject.onNext(abortState)
    }

    override fun onBalanceError(nextState: BalanceErrorState) {
        subject.onNext(nextState)
    }

    override fun onStart(nextState: StartState) {
        subject.onNext(nextState)
    }

    override fun onValidate(nextState: ValidateState) {
        subject.onNext(nextState)
    }

    override fun onValidateLightning(nextState: ValidateLightningState?) {
        subject.onNext(nextState)
    }
}
