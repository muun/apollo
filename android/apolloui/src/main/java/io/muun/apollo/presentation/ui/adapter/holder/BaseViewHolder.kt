package io.muun.apollo.presentation.ui.adapter.holder

import android.view.View
import androidx.recyclerview.widget.RecyclerView

abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {

    abstract fun bind(viewModel: T)

    open fun onItemClick() {
        // Override to apply visual/state changes upon click
    }
}
