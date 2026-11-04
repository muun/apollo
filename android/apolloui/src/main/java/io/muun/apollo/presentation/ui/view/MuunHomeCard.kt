package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorRes
import io.muun.apollo.databinding.ViewMuunHomeCardBinding
import io.muun.apollo.presentation.ui.utils.UiUtils

class MuunHomeCard @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView<ViewMuunHomeCardBinding>(c, a, s) {

    private val iconView get() = binding.icon
    private val bodyView get() = binding.body

    override fun bindingInflater(): BindingInflater<ViewMuunHomeCardBinding> {
        return ViewMuunHomeCardBinding::inflate
    }

    var icon: Drawable
        get() = iconView.drawable
        set(drawable) {
            iconView.setImageDrawable(drawable)
        }

    fun setIconTint(@ColorRes colorId: Int) {
        UiUtils.setTint(iconView, colorId)
    }

    var body: CharSequence
        get() = bodyView.text
        set(text) {
            bodyView.text = text
        }
}