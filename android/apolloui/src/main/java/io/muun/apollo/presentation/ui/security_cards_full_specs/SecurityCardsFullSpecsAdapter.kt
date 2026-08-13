package io.muun.apollo.presentation.ui.security_cards_full_specs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.muun.apollo.R
import io.muun.apollo.databinding.ItemSecurityCardSpecCardImageBinding
import io.muun.apollo.databinding.ItemSecurityCardSpecHeaderBinding
import io.muun.apollo.databinding.ItemSecurityCardSpecRowBinding
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.AdditionalInfo

class SecurityCardsFullSpecsAdapter(
    private val onAdditionalInfoClick: (AdditionalInfo) -> Unit,
) : ListAdapter<SpecListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        const val VIEW_TYPE_CARD_IMAGE = 0
        const val VIEW_TYPE_HEADER = 1
        const val VIEW_TYPE_ROW = 2
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SpecListItem.CardImage -> VIEW_TYPE_CARD_IMAGE
        is SpecListItem.SectionHeader -> VIEW_TYPE_HEADER
        is SpecListItem.Row -> VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_CARD_IMAGE -> CardImageViewHolder(
                ItemSecurityCardSpecCardImageBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemSecurityCardSpecHeaderBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_ROW -> RowViewHolder(
                ItemSecurityCardSpecRowBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SpecListItem.CardImage -> (holder as CardImageViewHolder).bind(item)
            is SpecListItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is SpecListItem.Row -> (holder as RowViewHolder).bind(item, onAdditionalInfoClick)
        }
    }

    class CardImageViewHolder(
        private val binding: ItemSecurityCardSpecCardImageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SpecListItem.CardImage) {
            binding.imageViewCard.setImageResource(item.imageRes)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemSecurityCardSpecHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SpecListItem.SectionHeader) {
            binding.textViewSectionTitle.text = item.title
        }
    }

    class RowViewHolder(
        private val binding: ItemSecurityCardSpecRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SpecListItem.Row, onInfoClick: (AdditionalInfo) -> Unit) {
            binding.imageViewIcon.setImageResource(item.iconRes)
            binding.textViewLabel.text = item.label
            binding.textViewValue.text = item.value

            val info = item.additionalInfo
            if (info != null) {
                binding.textViewValue.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, ContextCompat.getDrawable(itemView.context, R.drawable.ic_info_outline_24), null)
                binding.textViewValue.setOnClickListener { onInfoClick(info) }
                binding.textViewValue.isClickable = true
            } else {
                binding.textViewValue.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                binding.textViewValue.setOnClickListener(null)
                binding.textViewValue.isClickable = false
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SpecListItem>() {
        override fun areItemsTheSame(oldItem: SpecListItem, newItem: SpecListItem) = oldItem == newItem
        override fun areContentsTheSame(oldItem: SpecListItem, newItem: SpecListItem) = oldItem == newItem
    }
}

/**
 * Draws a rounded card background (fill + border) behind each group of consecutive
 * [SpecListItem.Row] items, and adds spacing offsets so the card doesn't overlap adjacent items.
 */
class SpecSectionItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.sc_spec_table_background)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.sc_marketplace_full_specs_section_border)
    }

    private val cornerRadius = context.resources.getDimension(R.dimen.sc_spec_card_corner_radius)
    private val horizontalInset = context.resources.getDimension(R.dimen.sc_spec_card_horizontal_inset)
    private val innerPadding = context.resources.getDimension(R.dimen.sc_spec_card_inner_padding)
    private val topOffset = context.resources.getDimensionPixelOffset(R.dimen.sc_spec_card_top_offset)

    // Pre-allocated to avoid per-frame allocations during scroll
    private val drawRect = RectF()

    // region getItemOffsets — spacing around the card

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        val adapter = parent.adapter as? SecurityCardsFullSpecsAdapter ?: return
        if (adapter.getItemViewType(pos) != SecurityCardsFullSpecsAdapter.VIEW_TYPE_ROW) return

        val isFirstInSection = pos == 0
                || adapter.getItemViewType(pos - 1) != SecurityCardsFullSpecsAdapter.VIEW_TYPE_ROW

        val isLastInSection = pos == adapter.itemCount - 1
                || adapter.getItemViewType(pos + 1) != SecurityCardsFullSpecsAdapter.VIEW_TYPE_ROW

        if (isFirstInSection) outRect.top = topOffset
        if (isLastInSection) outRect.bottom = innerPadding.toInt()
    }

    // endregion

    // region onDraw — card background behind row groups

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? SecurityCardsFullSpecsAdapter ?: return
        val list = adapter.currentList
        if (list.isEmpty()) return

        val left = parent.paddingLeft + horizontalInset
        val right = parent.width - parent.paddingRight - horizontalInset

        // Collect bounds of visible children keyed by adapter position.
        // Uses parallel arrays to avoid Map/Pair allocations on every draw frame.
        val visibleCount = parent.childCount
        val visiblePositions = IntArray(visibleCount)
        val visibleTops = FloatArray(visibleCount)
        val visibleBottoms = FloatArray(visibleCount)

        for (ci in 0 until visibleCount) {
            val child = parent.getChildAt(ci)
            visiblePositions[ci] = parent.getChildAdapterPosition(child)
            visibleTops[ci] = child.top.toFloat()
            visibleBottoms[ci] = child.bottom.toFloat()
        }

        // Walk the adapter data to find consecutive ROW groups, then draw the card for each.
        var i = 0
        while (i < list.size) {
            if (adapter.getItemViewType(i) != SecurityCardsFullSpecsAdapter.VIEW_TYPE_ROW) {
                i++
                continue
            }

            val groupStart = i
            while (i < list.size && adapter.getItemViewType(i) == SecurityCardsFullSpecsAdapter.VIEW_TYPE_ROW) i++
            val groupEnd = i - 1

            drawCardForGroup(c, groupStart, groupEnd, left, right, parent.height.toFloat(),
                visiblePositions, visibleTops, visibleBottoms, visibleCount)
        }
    }

    private fun drawCardForGroup(
        c: Canvas,
        groupStart: Int,
        groupEnd: Int,
        left: Float,
        right: Float,
        parentHeight: Float,
        visiblePositions: IntArray,
        visibleTops: FloatArray,
        visibleBottoms: FloatArray,
        visibleCount: Int,
    ) {
        var top: Float? = null
        var bottom: Float? = null
        var firstVisible = false
        var lastVisible = false

        for (ci in 0 until visibleCount) {
            val pos = visiblePositions[ci]
            if (pos < groupStart || pos > groupEnd) continue

            if (top == null) top = visibleTops[ci]
            bottom = visibleBottoms[ci]
            if (pos == groupStart) firstVisible = true
            if (pos == groupEnd) lastVisible = true
        }

        // Skip groups with no visible items
        if (top == null || bottom == null) return

        // When visible, extend inward by innerPadding. When off-screen, push the rounded
        // corners past the viewport edge so the Canvas clips them into a flat edge.
        top = if (firstVisible) top - innerPadding else -cornerRadius
        bottom = if (lastVisible) bottom + innerPadding else parentHeight + cornerRadius

        drawRect.set(left, top, right, bottom)
        c.drawRoundRect(drawRect, cornerRadius, cornerRadius, backgroundPaint)
        c.drawRoundRect(drawRect, cornerRadius, cornerRadius, borderPaint)
    }

    // endregion
}
