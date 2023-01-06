package io.muun.apollo.presentation.ui.fragments.operations

import android.content.Context
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.selector.BitcoinUnitSelector
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
    private val bitcoinUnitSel: BitcoinUnitSelector,
    private val userSel: UserSelector,
    private val linkBuilder: LinkBuilder,
    private val transformerFactory: ExecutionTransformerFactory,
    private val context: Context
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
                bitcoinUnitSel.watch(),
                userSel.watch()
            ) { ops, currencyDisplay, user -> // Kotlin can't handle generic SAM conversions :(
                OperationsPresenter.ReactiveState(ops, currencyDisplay, user)
            }
            .flatMap {

                mapOperations(it.operations, it.bitcoinUnit).map { uiOps ->
                    OperationsPresenter.ReactiveState<ItemViewModel>(
                        uiOps,
                        it.bitcoinUnit,
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
        cacheSubject = BehaviorSubject.create()
    }

    fun watch() =
        cacheSubject.asObservable()


    private fun mapOperations(operations: List<Operation>, bitcoinUnit: BitcoinUnit) =
        Observable.from(operations)
            .map { op -> UiOperation.fromOperation(op, linkBuilder, bitcoinUnit, context) }
            .map(::OperationViewModel)
            .toList()
}