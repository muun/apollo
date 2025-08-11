package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.model.FeeBumpFunctions

enum class FeeBumpRefreshPolicy(val value: String) {
    FOREGROUND("foreground"),
    PERIODIC("periodic"),
    NEW_OP_BLOCKINGLY("newOpBlockingly"),
    NTS_CHANGED("ntsChanged")
}

// LibwalletService is a protocol that abstract interactions with Libwallet library.
interface LibwalletService {
    // Fee Bump Functions section
    fun persistFeeBumpFunctions(
        feeBumpFunctions: FeeBumpFunctions,
        refreshPolicy: FeeBumpRefreshPolicy
    )
    fun areFeeBumpFunctionsInvalidated(): Boolean
}