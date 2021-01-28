package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import butterknife.BindView
import io.muun.apollo.R

class MuunButtonLayoutAnchor
@JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) : MuunView(c, a, s) {

    @BindView(R.id.muun_button_layout_anchor)
    lateinit var rootLayout: ViewGroup

    override fun getLayoutResource(): Int =
        R.layout.muun_button_layout_anchor

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child.id == R.id.muun_button_layout_anchor) {
            return super.addView(child, index, params) // attach our own frame without intervention
        }

        rootLayout.addView(child, params)
    }
}
