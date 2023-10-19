package io.muun.apollo.presentation.ui.activity.extension

import android.content.Intent
import androidx.fragment.app.DialogFragment
import icepick.State
import io.muun.apollo.presentation.ui.base.di.PerActivity
import timber.log.Timber
import javax.inject.Inject

@PerActivity
class ExternalResultExtension @Inject constructor() : BaseRequestExtension() {

    interface Caller : BaseCaller {
        fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?)
    }

    /**
     * Yeah. Names sucks huh? This represents a Caller that can delegate the onExternalResult
     * callback to another Caller. This is mainly to workaround some of Android's delicious
     * shortcomings, for example the inability to proper set fragments ID when inside
     * ViewPager/FragmentPagerAdapter (and apparently they all have the same ID) and the hardcoded
     * fragment tags in undocumented internal code (which we would rather not (ab)use).
     * More info:
     * - https://stackoverflow.com/q/18609261/901465
     * - https://stackoverflow.com/questions/34861257/how-can-i-set-a-tag-for-viewpager-fragments
     */
    interface DelegableCaller : Caller {
        // Nullable, since impl classes may be in Java (and in fact some are)
        // TODO kotlinize every last mother effing last one of 'em!
        val delegateCaller: Caller?
    }

    /**
     * Ideally, this would go in parent class but for some reason Icepick serialisation doesn't
     * work correctly for children so we store this in both child classes.
     */
    @JvmField
    @State(RequestMapBundler::class)
    var pendingRequests = HashMap<Int, CallerRequest>()

    /**
     * Show a DialogFragment, expecting back a result.
     */
    fun showDialogForResult(caller: Caller, viewRequestCode: Int, dialog: DialogFragment): String {
        val globalRequestCode = registerRequestFromCaller(caller, viewRequestCode)
        dialog.setTargetFragment(null, globalRequestCode)
        dialog.show(activity.supportFragmentManager, "dialog-$globalRequestCode")
        return "dialog-$globalRequestCode"
    }

    /**
     * Start an Activity Intent, expecting back a result.
     */
    fun startActivityForResult(view: Caller, viewRequestCode: Int, intent: Intent) {
        val globalRequestCode = registerRequestFromCaller(view, viewRequestCode)
        activity.startActivityForResult(intent, globalRequestCode)
    }

    override fun onActivityResult(globalRequestCode: Int, resultCode: Int, data: Intent?) {
        val request = pendingRequests[globalRequestCode]
        var view = findCaller(request, Caller::class.java)

        Timber.i(
            """onActivityResult:
                globalRequestCode: $globalRequestCode
                resultCode: $resultCode
                request.viewRequestCode: ${request?.viewRequestCode}
                request.viewId: ${request?.viewId}
                view: ${view?.javaClass.toString()}
                view.mId: ${view?.mId}
                 """
        )

        @Suppress("FoldInitializerAndIfToElvis") // Else kotlin smart cast fail for line below
        if (view == null) {
            return
        }

        if (view is DelegableCaller) {
            if (view.delegateCaller != null) {
                view = view.delegateCaller
            } else {

                // We're hunting down a sneaky bug here. Let's log every useful piece data
                // We believe there’s some random, not deterministic issue that makes fragments ids
                // change or something and our findCaller mechanism falls short
                Timber.i("View/Fragment Caller not found for onActivityResult.")
                val fragments = activity.supportFragmentManager.fragments
                val fragmentIds = fragments.map { it.id }
                val fragmentNames = fragments.map { it.javaClass.simpleName }
                Timber.e(
                    "View/Fragment Caller with id:${request!!.viewId} not found." +
                        " Fallback to activity: ${activity.javaClass.simpleName}." +
                        " Fragment ids: $fragmentIds. Fragment names: $fragmentNames"
                )
            }
        }
        pendingRequests.remove(globalRequestCode)
        view!!.onExternalResult(request!!.viewRequestCode, resultCode, data)
    }

    override fun registerRequestFromCaller(request: CallerRequest, globalRequestCode: Int) {
        pendingRequests[globalRequestCode] = request
    }
}