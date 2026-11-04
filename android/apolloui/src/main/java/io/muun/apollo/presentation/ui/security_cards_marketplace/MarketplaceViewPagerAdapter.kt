package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace

class MarketplaceViewPagerAdapter(
    activity: FragmentActivity,
) : FragmentStateAdapter(activity) {

    private var data: List<SecurityCardMarketplace.SecurityCardProvider> = emptyList()

    override fun createFragment(position: Int): Fragment {
        return SecurityCardProviderFragment.newInstance(data[position])
    }

    override fun getItemCount() = data.size

    override fun getItemId(position: Int): Long {
        return data[position].uuid.hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return data.any { it.uuid.hashCode().toLong() == itemId }
    }

    fun setData(newData: List<SecurityCardMarketplace.SecurityCardProvider>) {
        val diffCallback = MarketplaceViewPagerDiffUtil(oldList = data, newList = newData)
        val diff = DiffUtil.calculateDiff(diffCallback)

        data = newData

        diff.dispatchUpdatesTo(this)
    }

    fun getPageName(index: Int): String {
        return data[index].name
    }

    // TODO: This will change, dont review
    fun getProvider(index: Int): SecurityCardMarketplace.SecurityCardProvider {
        return data[index]
    }

    private class MarketplaceViewPagerDiffUtil(
        private val oldList: List<SecurityCardMarketplace.SecurityCardProvider>,
        private val newList: List<SecurityCardMarketplace.SecurityCardProvider>,
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
