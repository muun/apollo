package io.muun.apollo.domain.model.auth

import io.muun.common.api.KeySet
import io.muun.common.crypto.ChallengeType

class LoginOk(
    val loginType: ChallengeType, // Should either be PASSWORD or RECOVERY_CODE
    val keySet: KeySet,
)