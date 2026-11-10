package io.muun.apollo.presentation.ui.fragments.flow_intro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.viewbinding.ViewBinding
import androidx.viewpager.widget.ViewPager
import icepick.State
import io.muun.apollo.databinding.FragmentIntroductionBinding
import io.muun.apollo.presentation.ui.base.SingleFragment

abstract class FlowIntroFragment<V, P, PP> : SingleFragment<P>(), FlowIntroView
    where V : FlowIntroView,
          P : FlowIntroPresenter<V, PP>,
          PP : FlowIntroParentPresenter {

    private val binding: FragmentIntroductionBinding
        get() = getBinding() as FragmentIntroductionBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentIntroductionBinding::inflate
    }

    @State
    @JvmField
    var currentPosition: Int = -1 // properly initialized in initializeUi()

    abstract fun getPager(): FlowIntroPager

    @StringRes
    abstract fun getConfirmLabel(): Int

    override fun initializeUi(view: View) {
        // Keep currentPosition from restored State, or init from arguments (0 if not passed):
        if (currentPosition == -1) {
            currentPosition = argumentsBundle.getInt(FlowIntroView.ARG_STEP)
        }

        binding.pager.adapter = getPager()
        binding.pager.currentItem = currentPosition
        binding.pager.addOnPageChangeListener(pageChangeListener)

        binding.pagerDots.setupWithViewPager(binding.pager)

        binding.accept.setText(getConfirmLabel())
        binding.accept.setOnClickListener {
            presenter.confirmIntroduction()
        }

        onPagerPositionChanged(binding.pager.currentItem)
    }

    override fun setLoading(isLoading: Boolean) {
        binding.accept.setLoading(isLoading)
    }

    override fun onBackPressed(): Boolean =
        if (currentPosition > 0) {
            binding.pager.currentItem = currentPosition - 1
            true
        } else {
            false
        }

    private fun onPagerPositionChanged(position: Int) {
        this.currentPosition = position

        presenter.reportIntroductionStep(position)

        val inFirstStep = (position == 0)
        val inLastStep = (position == requireNotNull(binding.pager.adapter).count - 1)

        binding.pagerFootnote.visibility = if (inFirstStep) View.VISIBLE else View.INVISIBLE
        binding.accept.visibility = if (inLastStep) View.VISIBLE else View.INVISIBLE
    }

    // Listener to show action buttons on final slide:
    private val pageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {}
        override fun onPageScrolled(position: Int, posOffset: Float, posOffsetPx: Int) {}

        override fun onPageSelected(position: Int) {
            onPagerPositionChanged(position)
        }
    }
}