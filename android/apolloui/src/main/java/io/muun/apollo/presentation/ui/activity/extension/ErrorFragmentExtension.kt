package io.muun.apollo.presentation.ui.activity.extension

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.ExtensibleActivity
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.fragments.error.ErrorFragment
import io.muun.apollo.presentation.ui.fragments.error.ErrorFragmentDelegate
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel
import io.muun.common.utils.CollectionUtils
import io.muun.common.utils.Preconditions
import javax.inject.Inject

// Currently needs activities making use of it to extend SingleFragmentActivity (a limitation of
// ErrorFragment (extends SingleFragments) that asks parent activity to be a SingleFragmentActivity)
// TODO remove this limitation (make every activity SingleFragmentActivity or relax limitation)
@PerActivity
class ErrorFragmentExtension @Inject constructor() : ActivityExtension() {

    companion object {
        private const val TAG: String = "Muun Error Fragment"
    }

    override fun setActivity(activity: ExtensibleActivity) {
        Preconditions.checkArgument(activity is SingleFragmentActivity<*>)
        super.setActivity(activity)

        val fragments = activity.supportFragmentManager.fragments
        if (fragments.isNotEmpty()) {

            val errorFragments: List<Fragment> = CollectionUtils
                .filterList(fragments) { f: Fragment -> f is ErrorFragment }

            if (errorFragments.isNotEmpty()) {
                Preconditions.checkState(
                    errorFragments.size == 1,
                    "Only 1 ErrorFragment allowed. Current: " + errorFragments.size
                )

                if (activity is ErrorFragmentDelegate) {
                    val errorFragment = errorFragments[0] as ErrorFragment
                    errorFragment.setDelegate((activity as ErrorFragmentDelegate))
                }
            }
        }
    }

    fun showError(vm: ErrorViewModel) {
        val isErrorFragmentDelegate = activity is ErrorFragmentDelegate
        val errorFragment = ErrorFragment.create(vm, descriptionClickable = isErrorFragmentDelegate)
        show(errorFragment)
        if (isErrorFragmentDelegate) {
            errorFragment.setDelegate(activity as ErrorFragmentDelegate)
        }
    }

    fun hideError() {
        val errorFragment = activity.supportFragmentManager.findFragmentByTag(TAG)

        if (errorFragment != null) {
            activity.supportFragmentManager
                .beginTransaction()
                .remove(errorFragment)
                .safelyCommitNow(activity)
        }
    }

    private fun show(fragment: Fragment) {
        activity.supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, fragment, TAG)
            .safelyCommitNow(activity)
    }

    private fun FragmentTransaction.safelyCommitNow(activity: ExtensibleActivity) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            val fragmentManager = activity.supportFragmentManager
            if (!fragmentManager.isDestroyed && !fragmentManager.isStateSaved) {
                commitNow()
            }
        }
    }
}