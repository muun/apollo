package io.muun.apollo.domain.action.permission

import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.ContactActions
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.model.PermissionState
import javax.inject.Inject

class UpdateContactsPermissionStateAction @Inject constructor(
    private val userRepository: UserRepository,
    private val userActions: UserActions,
    private val contactActions: ContactActions,
) {

    val action = UpdatePermissionStateAction(
        userRepository::getContactsPermissionState,
        this::onContactsPermissionGranted,
        this::onContactsPermissionDenied,
    )

    /**
     * Update value of user preference tracking Contacts permission state.
     * This receives as param the result of asking if permission is granted, that's why it is a
     * boolean: true for GRANTED, false for DENIED.
     *
     * <p>Helpful: table of values
     *
     * <p>if current_state is NOT_DETERMINED    && new_value is GRANTED =>  GRANTED
     * if current_state is NOT_DETERMINED       && new_value is DENIED  =>  NOT_DETERMINED
     * if current_state is GRANTED              && new_value is GRANTED =>  GRANTED
     * if current_state is GRANTED              && new_value is DENIED  =>  DENIED
     * if current_state is DENIED               && new_value is GRANTED =>  GRANTED
     * if current_state is DENIED               && new_value is DENIED  =>  DENIED
     * if current_state is PERMANENTLY_DENIED   && new_value is GRANTED =>  GRANTED
     * if current_state is PERMANENTLY_DENIED   && new_value is DENIED  =>  PERMANENTLY_DENIED
     */
    fun run(granted: Boolean) {
        action.run(granted)
    }

    private fun onContactsPermissionGranted(prevState: PermissionState) {
        // if we detect a permission grant (via android settings) => trigger sync phone contacts
        if (userActions.isLoggedIn && prevState != PermissionState.GRANTED) {
            contactActions.initialSyncPhoneContactsAction.run()
        }
        userRepository.storeContactsPermissionState(PermissionState.GRANTED)
    }

    private fun onContactsPermissionDenied() {
        userRepository.storeContactsPermissionState(PermissionState.DENIED)
    }
}