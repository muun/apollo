package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.user.User
import io.muun.common.crypto.hd.PublicKey

data class CreateFirstSessionOk(
    val user: User,
    val cosigningPublicKey: PublicKey,
    val swapServerPublicKey: PublicKey,
    val playIntegrityNonce: String?,
)