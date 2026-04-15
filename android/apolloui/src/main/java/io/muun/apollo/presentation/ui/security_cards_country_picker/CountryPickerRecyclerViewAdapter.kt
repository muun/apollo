package io.muun.apollo.presentation.ui.security_cards_country_picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.muun.apollo.R
import io.muun.apollo.databinding.ItemCountryBinding
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.SelectableCountryInfo

class CountryPickerRecyclerViewAdapter(
    private val onCountryClick: (CountryInfo) -> Unit,
) : ListAdapter<SelectableCountryInfo, CountryPickerRecyclerViewAdapter.ViewHolder>(DiffCallback()) {

    override fun submitList(list: List<SelectableCountryInfo?>?) {
        super.submitList(null)
        super.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCountryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCountryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_ID.toInt()) {
                    onCountryClick(getItem(position).countryInfo)
                }
            }
        }

        fun bind(item: SelectableCountryInfo) {
            binding.textViewFlag.text = item.countryInfo.flagEmoji
            binding.textViewName.text = item.countryInfo.name
            binding.textViewName.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (item.isSelected) R.color.blue_buttons else R.color.text_primary_color,
                )
            )
            binding.imageViewCheckmark.visibility =
                if (item.isSelected) View.VISIBLE else View.INVISIBLE
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SelectableCountryInfo>() {
        override fun areItemsTheSame(oldItem: SelectableCountryInfo, newItem: SelectableCountryInfo) =
            oldItem.countryInfo.code == newItem.countryInfo.code

        override fun areContentsTheSame(oldItem: SelectableCountryInfo, newItem: SelectableCountryInfo) =
            oldItem == newItem
    }
}
