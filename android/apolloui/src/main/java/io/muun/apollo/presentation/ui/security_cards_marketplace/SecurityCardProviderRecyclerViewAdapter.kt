package io.muun.apollo.presentation.ui.security_cards_marketplace

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.muun.apollo.R
import io.muun.apollo.databinding.ItemSecurityCardsMarketplaceCardBinding
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace
import io.muun.apollo.presentation.ui.utils.capitalizedName
import io.muun.apollo.presentation.ui.utils.getColorCompat
import io.muun.apollo.presentation.ui.utils.setBackgroundTint
import io.muun.apollo.presentation.ui.utils.setTextColorCompat

class SecurityCardProviderRecyclerViewAdapter(
    @ColorInt private val providerPrimaryColor: Int,
    private val onSecurityCardClick: (SecurityCardMarketplace.SecurityCard) -> Unit,
) : ListAdapter<
    SecurityCardMarketplace.SecurityCard,
    SecurityCardProviderRecyclerViewAdapter.ViewHolder,
    >(
    SecurityCardProviderDiffUtil()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ItemSecurityCardsMarketplaceCardBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
            .let(::ViewHolder)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSecurityCardsMarketplaceCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SecurityCardMarketplace.SecurityCard) {
            binding.bindImage(item)
            binding.bindTag(item)
            itemView.setOnClickListener { onSecurityCardClick(item) }
        }

        private fun ItemSecurityCardsMarketplaceCardBinding.bindImage(
            item: SecurityCardMarketplace.SecurityCard
        ) {
            Glide.with(imageViewCard)
                .load(item.imageUrl)
                .into(imageViewCard)
        }

        private fun ItemSecurityCardsMarketplaceCardBinding.bindTag(
            item: SecurityCardMarketplace.SecurityCard
        ) {
            when {
                item.isOutOfStock -> {
                    viewOutOfStockOverlay.isVisible = true
                    textViewCardTag.isVisible = true
                    textViewCardTag.setText(R.string.security_cards_marketplace_out_of_stock)
                    textViewCardTag.setTextColorCompat(R.color.white)
                    textViewCardTag.setBackgroundTint(providerPrimaryColor)
                }

                item.material.isMetal -> {
                    viewOutOfStockOverlay.isVisible = false
                    textViewCardTag.isVisible = true
                    textViewCardTag.text = item.material.capitalizedName()
                    textViewCardTag.setTextColorCompat(R.color.security_card_metal_text)
                    textViewCardTag.setBackgroundTint(getColorCompat(R.color.background))
                }

                else -> {
                    viewOutOfStockOverlay.isVisible = false
                    textViewCardTag.isVisible = false
                }
            }
        }
    }

    private class SecurityCardProviderDiffUtil :
        DiffUtil.ItemCallback<SecurityCardMarketplace.SecurityCard>() {

        override fun areItemsTheSame(
            oldItem: SecurityCardMarketplace.SecurityCard,
            newItem: SecurityCardMarketplace.SecurityCard,
        ): Boolean {
            return oldItem.uuid == newItem.uuid
        }

        override fun areContentsTheSame(
            oldItem: SecurityCardMarketplace.SecurityCard,
            newItem: SecurityCardMarketplace.SecurityCard,
        ): Boolean {
            return oldItem == newItem
        }
    }
}
