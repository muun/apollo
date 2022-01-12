package io.muun.apollo.presentation.ui.activity.extension

import io.muun.apollo.presentation.ui.base.di.PerActivity
import androidx.annotation.CallSuper
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import icepick.State
import io.muun.common.utils.Preconditions
import java.util.ArrayList
import java.util.HashMap
import javax.inject.Inject

@PerActivity
class PermissionManagerExtension @Inject constructor() : BaseRequestExtension() {

    interface PermissionRequester : BaseCaller {
        /**
         * Override this method to be notified when ALL requested permissions have been granted.
         */
        fun onPermissionsGranted(grantedPermissions: Array<String>)

        /**
         * Override this method to be notified when SOME requested permissions have been denied.
         */
        fun onPermissionsDenied(deniedPermissions: Array<String>)
    }

    /**
     * Ideally, this would go in parent class but for some reason Icepick serialisation doesn't
     * work correctly for children so we store this in both child classes.
     */
    @JvmField
    @State(RequestMapBundler::class)
    var pendingRequests = HashMap<Int, CallerRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Preconditions.checkState(activity is PermissionRequester)
    }

    @CallSuper
    override fun onRequestPermissionsResult(
        globalRequestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        val request = pendingRequests[globalRequestCode]
        val requester = findCaller(request, PermissionRequester::class.java)

        // Note: It is possible that the permissions request interaction with the user is
        // interrupted. In this case you will receive empty permissions and results arrays which
        // should be treated as a cancellation.
        if (results.isEmpty()) {
            requester!!.onPermissionsDenied(permissions)
            return
        }
        val permissionsDenied: MutableList<String> = ArrayList()
        for (i in results.indices) {
            if (results[i] != PackageManager.PERMISSION_GRANTED) {
                permissionsDenied.add(permissions[i])
            }
        }
        if (permissionsDenied.isNotEmpty()) {
            val array = permissionsDenied.toTypedArray()
            requester!!.onPermissionsDenied(array)
        } else {
            requester!!.onPermissionsGranted(permissions)
        }
        pendingRequests.remove(globalRequestCode)
    }

    override fun registerRequestFromCaller(request: CallerRequest, globalRequestCode: Int) {
        pendingRequests[globalRequestCode] = request
    }

    /**
     * Determine whether you have been granted some permissions.
     */
    fun allPermissionsGranted(vararg permissions: String?): Boolean {
        for (permission in permissions) {
            val grantResult = ContextCompat.checkSelfPermission(activity, permission!!)
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * Request some permissions to be granted to this application.
     */
    fun requestPermissions(requester: PermissionRequester, vararg permissions: String) {
        val codeForPermissions = getUniqueCodeForPermissions(permissions)
        val globalRequestCode = registerRequestFromCaller(requester, codeForPermissions)
        ActivityCompat.requestPermissions(
            activity,
            permissions,
            globalRequestCode
        )
    }

    /**
     * Gets whether you can show UI with rationale for requesting a permission.
     * Return false if the permission was denied with the 'Never ask again' checkbox checked.
     * For more info: https://goo.gl/HxVKYE, https://goo.gl/UkbZzg
     */
    fun canShowRequestPermissionRationale(permission: String?): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission!!)
    }

    private fun getUniqueCodeForPermissions(permissions: Array<out String>): Int {
        // Can only use lower 16 bits for request code
        return permissions.contentHashCode() and 0xFFFF
    }
}