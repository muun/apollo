package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import io.muun.apollo.R
import io.muun.apollo.databinding.MuunSaveOptionBinding

class MuunSaveOption @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView<MuunSaveOptionBinding>(c, a, s) {

    companion object {
        val viewProps: ViewProps<MuunSaveOption> = ViewProps.Builder<MuunSaveOption>()
            .addRef(R.attr.icon) { v: MuunSaveOption, resId: Int -> v.icon = resId }
            .addString(R.attr.title) { v: MuunSaveOption, str: String -> v.title = str }
            .addString(R.attr.description) { v: MuunSaveOption, str: String ->
                v.description = str
            }
            .build()
    }

    private val iconView get() = binding.icon
    private val titleView get() = binding.title
    private val descriptionView get() = binding.description
    private val labelView get() = binding.label

    override fun bindingInflater(): BindingInflater<MuunSaveOptionBinding> {
        return MuunSaveOptionBinding::inflate
    }

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)

        viewProps.transfer(attrs, this)
    }

    var title: CharSequence = ""
        set(value) {
            titleView.text = value
            field = value
        }

    var description: CharSequence = ""
        set(value) {
            descriptionView.text = value
            field = value
        }

    @DrawableRes
    var icon: Int = 0
        set(value) {
            iconView.setImageResource(value)
            field = value
        }

    var labelKind: MuunSaveOptionLabel.Kind? = null
        set(value) {
            if (value != null) {
                labelView.visibility = View.VISIBLE
                labelView.kind = value
            } else {
                labelView.visibility = View.GONE
            }

            field = value
        }
}