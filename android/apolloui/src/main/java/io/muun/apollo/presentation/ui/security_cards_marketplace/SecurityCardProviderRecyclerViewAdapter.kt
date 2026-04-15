package io.muun.apollo.presentation.ui.security_cards_marketplace

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard

class SecurityCardProviderRecyclerViewAdapter(
    private val onSecurityCardClick: (SecurityCard) -> Unit
) : ListAdapter<SecurityCard, SecurityCardProviderRecyclerViewAdapter.ViewHolder>(
    SecurityCardProviderDiffUtil()
) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val view: View = view.findViewById(R.id.securityCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_security_cards_marketplace_card, parent, false)
            .let(::ViewHolder)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view.setBackgroundResource(currentList[position].imageRes)
        holder.itemView.setOnClickListener { onSecurityCardClick(currentList[position]) }
    }

    override fun getItemCount() = currentList.size

    private class SecurityCardProviderDiffUtil : DiffUtil.ItemCallback<SecurityCard>() {

        override fun areItemsTheSame(
            oldItem: SecurityCard,
            newItem: SecurityCard,
        ) = true

        override fun areContentsTheSame(
            oldItem: SecurityCard,
            newItem: SecurityCard,
        ) = oldItem == newItem
    }
}
