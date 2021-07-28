package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.EditText
import android.widget.ImageView
import butterknife.BindView
import io.muun.apollo.R

class MuunUriInput @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView(c, a, s) {

    @BindView(R.id.text_input)
    lateinit var textInput: EditText

    @BindView(R.id.scan_qr_button)
    lateinit var scanQrButton: ImageView

    override val layoutResource: Int
        get() = R.layout.muun_uri_input

    var onChangeListener: (content: String) -> Unit = {}
    var onScanQrClickListener: () -> Unit = {}

    var content: String = ""
        get() = textInput.text.toString()
        set(newUri) {
            textInput.setText(newUri)
            field = newUri
        }

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)

        textInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChangeListener(s.toString())
            }
        })

        scanQrButton.setOnClickListener {
            onScanQrClickListener()
        }
    }

}