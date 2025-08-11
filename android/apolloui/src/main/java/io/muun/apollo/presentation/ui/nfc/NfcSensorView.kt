package io.muun.apollo.presentation.ui.nfc

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import io.muun.apollo.databinding.NfcSensorViewBinding

internal class NfcSensorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: NfcSensorViewBinding

    init {
        val inflater = LayoutInflater.from(context)
        binding = NfcSensorViewBinding.inflate(inflater, this, true)
        binding.lottieView.setScale(0.4f)
    }
}