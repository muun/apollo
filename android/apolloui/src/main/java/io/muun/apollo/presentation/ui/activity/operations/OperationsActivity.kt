package io.muun.apollo.presentation.ui.activity.operations

import android.content.Context
import android.content.Intent
import android.transition.Slide
import android.view.Gravity
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityOperationsBinding
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentView
import io.muun.apollo.presentation.ui.fragments.operations.OperationsFragment
import io.muun.apollo.presentation.ui.view.MuunHeader


class OperationsActivity
    : SingleFragmentActivity<SingleFragmentPresenter<SingleFragmentView, ParentPresenter>>() {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, OperationsActivity::class.java)
    }

    private val binding: ActivityOperationsBinding
        get() = getBinding() as ActivityOperationsBinding

    private val headerView: MuunHeader
        get() = binding.header

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_operations

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivityOperationsBinding::inflate
    }

    override fun getFragmentsContainer() =
        R.id.container

    override fun getHeader(): MuunHeader =
        headerView

    override fun getInitialFragment() =
        OperationsFragment.newInstance()!!

    override fun setAnimation() {
        val slide = Slide()
        slide.slideEdge = Gravity.BOTTOM
        slide.duration = 300
        slide.interpolator = DecelerateInterpolator()
        slide.excludeTarget(android.R.id.statusBarBackground, true)
        window.exitTransition = slide
        window.enterTransition = slide
    }
}