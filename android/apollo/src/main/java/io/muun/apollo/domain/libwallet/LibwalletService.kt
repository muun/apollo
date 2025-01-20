package io.muun.apollo.domain.libwallet

// LibwalletService is a protocol that abstract interactions with Libwallet library.
interface LibwalletService {
    // Fee Bump Functions section
    fun persistFeeBumpFunctions(encodedFeeBumpFunctions: List<String>)
    fun areFeeBumpFunctionsInvalidated(): Boolean
}