package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.TextView
import butterknife.BindView
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.view.PickerCard.Status
import io.muun.common.model.ReceiveFormatPreference

class ReceivePreferenceItem @JvmOverloads constructor(
    c: Context,
    a: AttributeSet? = null,
    s: Int = 0,
) : MuunView(c, a, s) {

    companion object {
        const val REQUEST_RECEIVE_PREFERENCE = 1
    }

    fun interface ReceivePreferenceChangedListener {
        fun onReceivePreferenceChanged(newReceivePreference: ReceiveFormatPreference)
    }

    private lateinit var listener: ReceivePreferenceChangedListener

    @BindView(R.id.receive_preference_value)
    lateinit var editReceivePreferenceButton: TextView

    @State
    lateinit var current: ReceiveFormatPreference

    override val layoutResource: Int
        get() = R.layout.edit_receive_preference_item

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)

        editReceivePreferenceButton.setOnClickListener { onReceivePreferenceClick() }
    }

    fun show(receivePreference: ReceiveFormatPreference) {
        updateReceivePreference(receivePreference)
    }

    fun setOnReceivePreferenceChangedListener(listener: ReceivePreferenceChangedListener) {
        this.listener = listener
    }

    private fun onReceivePreferenceClick() {
        val options = mutableListOf(
            getBitcoinOption(),
            getLightningOption(),
            getUnifiedQrOption()
        )

        val dialog = PickerDialogFragment()

        dialog.setPickerFactory {
            MuunPicker(context)
                .also { options.forEach(it::addOption) }
                .also { it.setTitle(context.getString(R.string.receive_preference_picker_title)) }
        }

        requestExternalResult(REQUEST_RECEIVE_PREFERENCE, dialog)
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_RECEIVE_PREFERENCE) {
            return // ignore
        }

        // can't fail, we gave the IDs above
        val newReceivePreference = ReceiveFormatPreference.values()[resultCode]

        updateReceivePreference(newReceivePreference)
        listener.onReceivePreferenceChanged(newReceivePreference)
    }

    private fun updateReceivePreference(newReceivePreference: ReceiveFormatPreference) {
        this.current = newReceivePreference

        val buttonText = when (current) {
            ReceiveFormatPreference.ONCHAIN -> R.string.receive_preference_bitcoin
            ReceiveFormatPreference.LIGHTNING -> R.string.receive_preference_lightning
            ReceiveFormatPreference.UNIFIED -> R.string.receive_preference_unified
        }

        editReceivePreferenceButton.setText(buttonText)
    }

    private fun getBitcoinOption(): MuunPicker.Option {
        val title = getStyledString(R.string.receive_preference_bitcoin)
        val description = getStyledString(R.string.receive_preference_bitcoin_desc)

        val status = if (current == ReceiveFormatPreference.ONCHAIN)
            Status.SELECTED
        else
            Status.NORMAL

        return MuunPicker.Option(
            ReceiveFormatPreference.ONCHAIN.ordinal,
            title,
            description,
            status
        )
    }

    private fun getLightningOption(): MuunPicker.Option {
        val title = getStyledString(R.string.receive_preference_lightning)
        val description = getStyledString(R.string.receive_preference_lightning_desc)

        val status = if (current == ReceiveFormatPreference.LIGHTNING)
            Status.SELECTED
        else
            Status.NORMAL

        return MuunPicker.Option(
            ReceiveFormatPreference.LIGHTNING.ordinal,
            title,
            description,
            status
        )
    }

    private fun getUnifiedQrOption(): MuunPicker.Option {
        val title = getStyledString(R.string.receive_preference_unified)
        val description = getStyledString(R.string.receive_preference_unified_desc)

        val status = if (current == ReceiveFormatPreference.UNIFIED)
            Status.SELECTED
        else
            Status.NORMAL

        return MuunPicker.Option(
            ReceiveFormatPreference.UNIFIED.ordinal,
            title,
            description,
            status
        )
    }
}