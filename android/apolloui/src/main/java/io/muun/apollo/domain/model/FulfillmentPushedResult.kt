package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.feebump.FeeBumpFunctions

class FulfillmentPushedResult(
    val nextTransactionSize: NextTransactionSize,
    val feeBumpFunctions: FeeBumpFunctions
)