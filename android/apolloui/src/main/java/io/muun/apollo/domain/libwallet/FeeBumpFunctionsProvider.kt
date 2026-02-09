package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.model.feebump.FeeBumpFunctions
import io.muun.apollo.domain.model.feebump.FeeBumpRefreshPolicy

// FeeBumpFunctionsProvider abstracts interactions with Fee Bump Functions.
// Note: this interface aims to decouple native code from Libwallet gomobile calls (enabling unit
// tests) in our current state of libwallet usage while we transition to a grpc-client-server
// architecture.
// TODO: remove this once both logic and state storage are migrated to libwallet.
interface FeeBumpFunctionsProvider {

    fun persistFeeBumpFunctions(
        feeBumpFunctions: FeeBumpFunctions,
        refreshPolicy: FeeBumpRefreshPolicy,
    )

    fun areFeeBumpFunctionsInvalidated(): Boolean
}