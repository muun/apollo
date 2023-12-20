package io.muun.apollo.presentation.ui.fragments.recovery_tool

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentRecoveryToolBinding
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.LinkBuilder
import javax.inject.Inject

class RecoveryToolFragment : SingleFragment<RecoveryToolPresenter>(), BaseView {

    @Inject
    lateinit var linkBuilder: LinkBuilder

    private val binding: FragmentRecoveryToolBinding
        get() = getBinding() as FragmentRecoveryToolBinding

    override fun inject() =
        component.inject(this)

    // TODO rm this once all fragments have successfully migrated from butterKnife to view binding.
    override fun getLayoutResource() =
        R.layout.fragment_recovery_tool

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentRecoveryToolBinding::inflate
    }

    override fun initializeUi(view: View) {
        binding.body.text = TextUtils.concat(
            getString(R.string.recovery_tool_info_body),
            " ",
            linkBuilder.recoveryToolLink()
        )
    }
}