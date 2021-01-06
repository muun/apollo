package io.muun.apollo.domain.selector

import io.muun.apollo.data.db.operation.OperationDao
import io.muun.apollo.domain.model.Operation
import io.muun.common.Optional
import rx.Observable
import javax.inject.Inject

class LatestOperationSelector
@Inject constructor(
        private val operationDao: OperationDao
) {

    fun watch(): Observable<Optional<Operation>> {
        return operationDao.fetchMaybeLatest()
    }

    fun get(): Optional<Operation> {
        return watch().toBlocking().first()
    }
}