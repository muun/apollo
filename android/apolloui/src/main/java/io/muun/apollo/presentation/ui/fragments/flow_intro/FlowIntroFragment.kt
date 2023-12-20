package io.muun.apollo.presentation.ui.fragments.flow_intro

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.viewpager.widget.ViewPager
import butterknife.BindView
import butterknife.OnClick
import com.google.android.material.tabs.TabLayout
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton

abstract class FlowIntroFragment<V, P, PP> : SingleFragment<P>(), FlowIntroView
    where V : FlowIntroView,
          P : FlowIntroPresenter<V, PP>,
          PP : FlowIntroParentPresenter {

    @BindView(R.id.pager)
    lateinit var viewPager: ViewPager

    @BindView(R.id.pagerDots)
    lateinit var viewPagerDots: TabLayout

    @BindView(R.id.pager_footnote)
    lateinit var footnoteView: TextView

    @BindView(R.id.accept)
    lateinit var acceptButton: MuunButton

    @State
    @JvmField
    var currentPosition: Int = -1 // properly initialized in initializeUi()

    abstract fun getPager(): FlowIntroPager

    @StringRes
    abstract fun getConfirmLabel(): Int

    override fun getLayoutResource() =
        R.layout.fragment_introduction

    override fun initializeUi(view: View) {
        // Keep currentPosition from restored State, or init from arguments (0 if not passed):
        if (currentPosition == -1) {
            currentPosition = argumentsBundle.getInt(FlowIntroView.ARG_STEP)
        }

        viewPager.adapter = getPager()
        viewPager.currentItem = currentPosition
        viewPager.addOnPageChangeListener(pageChangeListener)

        viewPagerDots.setupWithViewPager(viewPager)

        acceptButton.setText(getConfirmLabel())

        onPagerPositionChanged(viewPager.currentItem)
    }

    override fun setLoading(isLoading: Boolean) {
        acceptButton.setLoading(isLoading)
    }

    @OnClick(R.id.accept)
    fun onConfirmClick() {
        presenter.confirmIntroduction()
    }

    override fun onBackPressed(): Boolean =
        if (currentPosition > 0) {
            viewPager.currentItem = currentPosition - 1
            true

        } else {
            false
        }

    private fun onPagerPositionChanged(position: Int) {
        this.currentPosition = position

        presenter.reportIntroductionStep(position)

        val inFirstStep = (position == 0)
        val inLastStep = (position == viewPager.adapter!!.count - 1)

        footnoteView.visibility = if (inFirstStep) View.VISIBLE else View.INVISIBLE
        acceptButton.visibility = if (inLastStep) View.VISIBLE else View.INVISIBLE
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