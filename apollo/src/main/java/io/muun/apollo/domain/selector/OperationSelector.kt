package io.muun.apollo.domain.selector

import io.muun.apollo.data.db.operation.OperationDao
import javax.inject.Inject

class OperationSelector @Inject constructor(
    val operationDao: OperationDao
) {

    fun watch() =
        operationDao.fetchAll()

    fun watchUnsettled() =
        operationDao.fetchUnsettled()
}