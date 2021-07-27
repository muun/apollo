package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R

class MuunSaveOptionLabel @JvmOverloads constructor(
    c: Context,
    a: AttributeSet? = null,
    s: Int = 0
) :
    MuunView(c, a, s) {

    enum class Kind {
        RECOMMENDED,
        DISABLED
    }

    @BindView(R.id.text)
    lateinit var textView: TextView

    override val layoutResource: Int
        get() = R.layout.muun_save_option_label

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)

        // Just to call the setter function (which var defaults don't do):
        this.kind = Kind.RECOMMENDED
    }

    var kind: Kind = Kind.RECOMMENDED
        set(value) {
            updateKind(value)
            field = value
        }

    private fun updateKind(kind: Kind) {
        when (kind) {
            Kind.RECOMMENDED -> {
                textView.setText(R.string.option_label_recommended)
                textView.setBackgroundResource(R.drawable.muun_save_option_label_recommended_bg)
            }

            Kind.DISABLED -> {
                textView.setText(R.string.option_label_disabled)
                textView.setBackgroundResource(R.drawable.muun_save_option_label_disabled_bg)
            }
        }
    }
}