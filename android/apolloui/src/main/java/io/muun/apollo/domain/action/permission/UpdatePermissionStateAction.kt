package io.muun.apollo.domain.action.permission

import io.muun.apollo.domain.model.PermissionState
import rx.functions.Action0
import rx.functions.Action1
import javax.inject.Inject

class UpdatePermissionStateAction @Inject constructor(
    private val permissionGetter: () -> PermissionState,
    private val onPermissionGranted: Action1<PermissionState>,
    private val onPermissionDenied: Action0,
) {

    /**
     * Handle update of a certain permission state.
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
        val prevState: PermissionState = permissionGetter.invoke()
        if (granted) {
            onPermissionGranted.call(prevState)
            return
        }

        val isNotPermanentlyDenied = prevState != PermissionState.PERMANENTLY_DENIED
        val isNotDeterminedYet = prevState != PermissionState.NOT_DETERMINED
        if (isNotPermanentlyDenied && isNotDeterminedYet) {
            onPermissionDenied.call() // basically, only call when coming from GRANTED or DENIED
        }
    }
}