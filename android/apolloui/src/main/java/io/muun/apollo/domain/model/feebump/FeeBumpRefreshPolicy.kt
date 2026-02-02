package io.muun.apollo.domain.model.feebump

enum class FeeBumpRefreshPolicy(val value: String) {
    FOREGROUND("foreground"),
    PERIODIC("periodic"),
    NEW_OP_BLOCKINGLY("newOpBlockingly"),
    NTS_CHANGED("ntsChanged")
}