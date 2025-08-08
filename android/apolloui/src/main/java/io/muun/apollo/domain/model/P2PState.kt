package io.muun.apollo.domain.model

import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.model.user.User

class P2PState(
    val user: User,
    val permissionState: PermissionState,
    val syncState: ActionState<*>,
    val contacts: List<Contact>
)