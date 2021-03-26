package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import icepick.State
import io.muun.apollo.R

class HiddenSection @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView(c, a, s) {

    companion object {
        val viewProps: ViewProps<HiddenSection> = ViewProps.Builder<HiddenSection>()
            .addRef(R.attr.expandedLabel) { us, resId -> us.expandedTextResId = resId }
            .addRef(R.attr.retractedLabel) { us, resId -> us.retractedTextResId = resId }
            .build()
    }

    @BindView(R.id.hidden_section_text)
    lateinit var hiddenSectionText: TextView

    @BindView(R.id.hidden_section_icon)
    lateinit var hiddenSectionChevron: ImageView

    // State:
    @State
    @JvmField
    var retractedTextResId: Int = 0

    @State
    @JvmField
    var expandedTextResId: Int = 0

    @State
    @JvmField
    var expanded: Boolean = false

    override fun getLayoutResource() =
        R.layout.view_hidden_section

    override fun setUp(context: Context?, attrs: AttributeSet?) {
        super.setUp(context, attrs)
        viewProps.transfer(attrs, this)

        if (!isInEditMode || retractedTextResId != 0) {
            internalSetLabel(this.context.getString(retractedTextResId))
        }

        updateViewState()
    }

    fun toggleSection() {
        setExpanded(!expanded)
    }

    fun setExpanded(expanded: Boolean) {
        this.expanded = expanded
        updateViewState()
    }

    private fun updateViewState() {
        if (!expanded) {
            internalSetLabel(context.getString(retractedTextResId))
            hiddenSectionChevron.setImageResource(R.drawable.chevron_down)

        } else {
            internalSetLabel(context.getString(expandedTextResId))
            hiddenSectionChevron.setImageResource(R.drawable.chevron_up)
        }
    }

    private fun internalSetLabel(string: String) {
        hiddenSectionText.text = string
        hiddenSectionChevron.contentDescription = string
    }
}