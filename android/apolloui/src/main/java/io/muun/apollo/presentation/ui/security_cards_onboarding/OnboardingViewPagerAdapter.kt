package io.muun.apollo.presentation.ui.security_cards_onboarding

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.muun.apollo.presentation.ui.security_cards_onboarding.models.OnboardingSlide

class OnboardingViewPagerAdapter(
    activity: FragmentActivity,
) : FragmentStateAdapter(activity) {

    private var data: List<OnboardingSlide> = emptyList()

    override fun createFragment(position: Int) = when (val slide = data[position]) {
        is OnboardingSlide.Explanatory -> OnboardingExplanatorySlideFragment.newInstance(slide)
        is OnboardingSlide.CountrySelector -> OnboardingCountrySelectorSlideFragment.newInstance(slide)
    }

    override fun getItemCount() = data.size

    override fun getItemId(position: Int) = data[position].hashCode().toLong()

    override fun containsItem(itemId: Long) =
        data.singleOrNull { dataItem -> dataItem.hashCode().toLong() == itemId } != null

    fun setData(newData: List<OnboardingSlide>) {
        val diffCallback = OnboardingPagerDiffCallback(oldList = data, newList = newData)
        val diff = DiffUtil.calculateDiff(diffCallback)

        data = newData

        diff.dispatchUpdatesTo(this)
    }

    private class OnboardingPagerDiffCallback(
        private val oldList: List<OnboardingSlide>,
        private val newList: List<OnboardingSlide>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ) = oldList[oldItemPosition]::class == newList[newItemPosition]::class

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition] == newList[newItemPosition]
    }
}
