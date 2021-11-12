package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.TextView
import butterknife.BindView
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.show_qr.bitcoin.AddressType
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.view.AddressTypeCard.Status

class AddressTypeItem @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    companion object {
        const val REQUEST_ADDRESS_TYPE = 1
    }

    interface AddressTypeChangedListener {
        fun onAddressTypeChanged(newType: AddressType)
    }

    private lateinit var listener: AddressTypeChangedListener

    @BindView(R.id.edit_address_type)
    lateinit var editAddressTypeButton: TextView

    @BindView(R.id.address_type_label)
    lateinit var editAddressTypeLabel: TextView

    @State
    lateinit var addressType: AddressType

    @State
    @JvmField
    var taprootStatus: UserActivatedFeatureStatus = UserActivatedFeatureStatus.OFF

    @State
    @JvmField
    var hoursToTaproot: Int = 0

    override val layoutResource: Int
        get() = R.layout.edit_address_type_item

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)

        editAddressTypeButton.setOnClickListener { onAddressTypeClick() }
    }

    fun show(addressType: AddressType) {
        updateAddressType(addressType)
    }

    fun setOnAddressTypeChangedListener(listener: AddressTypeChangedListener) {
        this.listener = listener
    }

    private fun onAddressTypeClick() {
        val options = mutableListOf(
            getLegacyOption(),
            getSegwitOption()
        )

        if (isTaprootOptionIncluded()) {
            options.add(getTaprootOption())
        }

        val dialog = PickerDialogFragment()

        dialog.setPickerFactory {
            AddressTypePicker(context).also { options.forEach(it::addOption) }
        }

        requestExternalResult(REQUEST_ADDRESS_TYPE, dialog)
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_ADDRESS_TYPE) {
            return // ignore
        }

        val newAddressType = AddressType.values()[resultCode] // can't fail, we gave the IDs above

        updateAddressType(newAddressType)
        listener.onAddressTypeChanged(addressType)
    }

    private fun updateAddressType(newAddressType: AddressType) {
        this.addressType = newAddressType

        val buttonText = when (addressType) {
            AddressType.SEGWIT -> R.string.segwit
            AddressType.LEGACY -> R.string.legacy
            AddressType.TAPROOT -> R.string.taproot
        }

        editAddressTypeButton.setText(buttonText)
    }

    private fun getLegacyOption(): AddressTypePicker.Option {
        val title = getStyledString(R.string.address_picker_legacy_title)
        val description = getStyledString(R.string.address_picker_legacy_desc)

        val status = if (addressType == AddressType.LEGACY) Status.SELECTED else Status.NORMAL

        return AddressTypePicker.Option(AddressType.LEGACY.ordinal, title, description, status)
    }

    private fun getSegwitOption(): AddressTypePicker.Option {
        val title = getStyledString(R.string.address_picker_segwit_title)
        val description = getStyledString(R.string.address_picker_segwit_desc)

        val status = if (addressType == AddressType.SEGWIT) Status.SELECTED else Status.NORMAL

        return AddressTypePicker.Option(AddressType.SEGWIT.ordinal, title, description, status)
    }

    private fun getTaprootOption(): AddressTypePicker.Option {
        val title = getStyledString(R.string.address_picker_taproot_title)

        val description = when {
            hoursToTaproot > 0 ->
                getStyledString(R.string.address_picker_taproot_desc_before_activation, "$hoursToTaproot")
            else ->
                getStyledString(R.string.address_picker_taproot_desc_after_activation)
        }

        val status = when {
            taprootStatus != UserActivatedFeatureStatus.ACTIVE -> Status.DISABLED
            addressType == AddressType.TAPROOT -> Status.SELECTED
            else -> Status.NORMAL
        }

        return AddressTypePicker.Option(AddressType.TAPROOT.ordinal, title, description, status)
    }

    private fun isTaprootOptionIncluded() =
        when (taprootStatus) {
            UserActivatedFeatureStatus.PREACTIVATED,
            UserActivatedFeatureStatus.SCHEDULED_ACTIVATION,
            UserActivatedFeatureStatus.ACTIVE -> true
            else -> false
        }
}