package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider

class MarketplaceViewPagerAdapter(
    activity: FragmentActivity,
) : FragmentStateAdapter(activity) {

    private var data: List<SecurityCardProvider> = emptyList()

    override fun createFragment(position: Int) = SecurityCardProviderFragment.newInstance(data[position])

    override fun getItemCount() = data.size

    override fun getItemId(position: Int): Long {
        return data[position].hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return data.singleOrNull { dataItem -> dataItem.hashCode().toLong() == itemId } != null
    }

    fun setData(newData: List<SecurityCardProvider>) {
        val diffCallback = MarketplaceViewPagerDiffUtil(oldList = data, newList = newData)
        val diff = DiffUtil.calculateDiff(diffCallback)

        data = newData

        diff.dispatchUpdatesTo(this)
    }

    fun getPageName(index: Int): String {
        return data[index].name
    }

    private class MarketplaceViewPagerDiffUtil(
        private val oldList: List<SecurityCardProvider>,
        private val newList: List<SecurityCardProvider>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = true

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition] == newList[newItemPosition]
    }
}
