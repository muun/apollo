package io.muun.apollo.presentation.ui.fragments.operations

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector
import io.muun.apollo.domain.selector.OperationSelector
import io.muun.apollo.domain.selector.UserSelector
import io.muun.apollo.presentation.app.di.PerApplication
import io.muun.apollo.presentation.model.UiOperation
import io.muun.apollo.presentation.ui.adapter.viewmodel.ItemViewModel
import io.muun.apollo.presentation.ui.adapter.viewmodel.OperationViewModel
import io.muun.apollo.presentation.ui.utils.LinkBuilder
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import javax.inject.Inject

@PerApplication
class OperationsCache @Inject constructor(
    private val operationsSel: OperationSelector,
    private val currencyDisplayModeSel: CurrencyDisplayModeSelector,
    private val userSel: UserSelector,
    private val linkBuilder: LinkBuilder,
    private val transformerFactory: ExecutionTransformerFactory
) {

    private var subscription: Subscription? = null

    private var cacheSubject = BehaviorSubject
        .create<OperationsPresenter.ReactiveState<ItemViewModel>>()

    fun start() {
        if (subscription != null) {
            return
        }

        // Fetch and map all the information needed to render the Operation list, in background,
        // a little ahead of time:
        subscription = Observable
            .combineLatest(
                operationsSel.watch(),
                currencyDisplayModeSel.watch(),
                userSel.watch()
            ) { ops, currencyDisplay, user -> // Kotlin can't handle generic SAM conversions :(
                OperationsPresenter.ReactiveState(ops, currencyDisplay, user)
            }
            .flatMap {

                mapOperations(it.operations, it.currencyDisplayMode).map { uiOps ->
                    OperationsPresenter.ReactiveState<ItemViewModel>(
                        uiOps,
                        it.currencyDisplayMode,
                        it.user
                    )
                }
            }
            .compose(transformerFactory.getAsyncExecutor())
            .subscribe(cacheSubject::onNext, cacheSubject::onError)
    }

    fun stop() {
        // Stop our subscription:
        subscription?.unsubscribe()
        subscription = null

        // Reset the subject, to free up memory when observers unsubscribe:
        cacheSubject = BehaviorSubject.create<OperationsPresenter.ReactiveState<ItemViewModel>>()
    }

    fun watch() =
        cacheSubject.asObservable()


    private fun mapOperations(operations: List<Operation>, displayMode: CurrencyDisplayMode) =
        Observable.from(operations)
            .map { operation -> UiOperation.fromOperation(operation, linkBuilder, displayMode) }
            .map(::OperationViewModel)
            .toList()
}