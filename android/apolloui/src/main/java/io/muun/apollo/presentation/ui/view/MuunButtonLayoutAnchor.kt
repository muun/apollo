package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import io.muun.apollo.R
import io.muun.apollo.databinding.MuunButtonLayoutAnchorBinding

class MuunButtonLayoutAnchor @JvmOverloads constructor(
    c: Context,
    a: AttributeSet? = null,
    s: Int = 0
) : MuunView<MuunButtonLayoutAnchorBinding>(c, a, s) {

    private val rootLayout: ViewGroup
        get() = binding.muunButtonLayoutAnchor

    override fun bindingInflater(): BindingInflater<MuunButtonLayoutAnchorBinding> {
        return MuunButtonLayoutAnchorBinding::inflate
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child.id == R.id.muun_button_layout_anchor) {
            return super.addView(child, index, params) // attach our own frame without intervention
        }

        rootLayout.addView(child, params)
    }
}
