package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import io.muun.apollo.databinding.MuunUriInputBinding

class MuunUriInput @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView<MuunUriInputBinding>(c, a, s) {

    private val textInput get() = binding.textInput
    private val scanQrButton get() = binding.scanQrButton

    override fun bindingInflater(): BindingInflater<MuunUriInputBinding> {
        return MuunUriInputBinding::inflate
    }

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