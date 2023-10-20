package io.muun.apollo.utils

import io.muun.apollo.data.external.Gen
import io.muun.apollo.domain.model.user.UserPhoneNumber

data class RandomUser(
    val email: String = Gen.email(),
    var password: String = Gen.alpha(10),
    var recoveryCode: List<String>? = null,
    val pin: List<Int> = Gen.pin(),
    val firstName: String = Gen.alpha(6),
    val lastName: String = Gen.alpha(6),
    val phoneNumber: UserPhoneNumber = Gen.userPhoneNumber(),
) {
    val fullName get() = "$firstName $lastName"
}

