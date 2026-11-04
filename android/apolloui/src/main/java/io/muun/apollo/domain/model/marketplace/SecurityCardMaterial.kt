package io.muun.apollo.domain.model.marketplace

enum class SecurityCardMaterial {
    PLASTIC,
    METAL;

    val isMetal: Boolean
        get() = this == METAL
}
