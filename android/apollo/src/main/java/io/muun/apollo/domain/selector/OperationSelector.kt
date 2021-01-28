package io.muun.apollo.domain.selector

import io.muun.apollo.data.db.operation.OperationDao
import io.muun.apollo.domain.model.Operation
import rx.Observable
import javax.inject.Inject

class OperationSelector @Inject constructor(
    val operationDao: OperationDao
) {

    fun watch(): Observable<List<Operation>> =
        operationDao.fetchAll()

    fun watchUnsettled(): Observable<List<Operation>> =
        operationDao.fetchUnsettled()
}