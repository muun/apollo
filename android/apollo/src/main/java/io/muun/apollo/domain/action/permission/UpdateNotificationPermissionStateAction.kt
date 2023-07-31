package io.muun.apollo.domain.action.permission

import io.muun.apollo.data.preferences.permission.NotificationPermissionStateRepository
import io.muun.apollo.domain.model.PermissionState
import javax.inject.Inject

class UpdateNotificationPermissionStateAction @Inject constructor(
    private val notificationPermissionStateRepository: NotificationPermissionStateRepository,
) {

    val action = UpdatePermissionStateAction(
        notificationPermissionStateRepository::get,
        this::onNotificationPermissionGranted,
        this::onNotificationPermissionDenied,
    )

    /**
     * Update value of user preference tracking Notifications permission state.
     *
     * <p> We need it to handle PERMANENTLY_DENIED (aka Never Ask Again) permission state (system
     * won't prompt for permission again, so we need to do something about it. This helps by keeping
     * track of the state of the permission so a decision can be taken (e.g we can take the user to
     * the App System Settings so they can manually change the permission).
     *
     * <p>This receives as param the result of asking if permission is granted, that's why it is a
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

    private fun onNotificationPermissionGranted(prevState: PermissionState) {
        notificationPermissionStateRepository.store(PermissionState.GRANTED)
    }

    private fun onNotificationPermissionDenied() {
        notificationPermissionStateRepository.store(PermissionState.DENIED)

    }
}