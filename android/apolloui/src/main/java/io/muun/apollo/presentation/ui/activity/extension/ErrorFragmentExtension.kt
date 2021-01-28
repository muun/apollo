package io.muun.apollo.presentation.ui.activity.extension

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.ExtensibleActivity
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.fragments.error.ErrorFragment
import io.muun.common.utils.Preconditions
import javax.inject.Inject

// Currently needs activities making use of it to extend SingleFragmentActivity (a limitation of
// ErrorFragment (extends SingleFragments) that asks parent activity to be a SingleFragmentActivity)
// TODO remove this limitation (make every activity SingleFragmentActivity or relax limitation)
@PerActivity
class ErrorFragmentExtension @Inject constructor() : ActivityExtension() {

    override fun setActivity(activity: ExtensibleActivity) {
        Preconditions.checkArgument(activity is SingleFragmentActivity<*>)
        super.setActivity(activity)
    }

    fun showError(@StringRes titleRes: Int, @StringRes descriptionRes: Int, vararg args: String) {
        val fragment = ErrorFragment.create(
            titleRes,
            descriptionRes,
            *args
        )

        show(fragment)
    }

    private fun show(fragment: Fragment) {
        activity.supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
    }
}