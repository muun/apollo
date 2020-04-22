package io.muun.apollo.domain.selector

import io.muun.apollo.data.db.contact.ContactDao
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.ContactActions
import io.muun.apollo.domain.model.P2PState
import rx.Observable
import javax.inject.Inject


class P2PStateSelector @Inject constructor(
    private val contactActions: ContactActions,
    private val contactDao: ContactDao,
    private val userRepository: UserRepository
) {

    fun watch() = Observable
        .combineLatest(
            userRepository.fetch(),
            userRepository.watchContactsPermissionState(),
            contactActions.initialSyncPhoneContactsAction.state, // TODO: move to own action
            contactDao.fetchAll(),
            ::P2PState
        )

    fun get() =
        watch().toBlocking().first()
}