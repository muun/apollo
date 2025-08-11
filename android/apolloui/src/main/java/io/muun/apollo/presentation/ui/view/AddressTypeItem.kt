package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import androidx.viewbinding.ViewBinding
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.databinding.EditAddressTypeItemBinding
import io.muun.apollo.domain.model.AddressType
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.view.PickerCard.Status

class AddressTypeItem @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    companion object {
        const val REQUEST_ADDRESS_TYPE = 1
    }

    interface AddressTypeChangedListener {
        fun onAddressTypeChanged(newType: AddressType)
    }

    private val binding: EditAddressTypeItemBinding
        get() = getBinding() as EditAddressTypeItemBinding

    override fun viewBinder(): ((View) -> ViewBinding) {
        return EditAddressTypeItemBinding::bind
    }

    private lateinit var listener: AddressTypeChangedListener

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

        binding.editAddressType.setOnClickListener { onAddressTypeClick() }
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
            MuunPicker(context)
                .also { options.forEach(it::addOption) }
                .also { it.setTitle(context.getString(R.string.address_picker_title)) }
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

        binding.editAddressType.setText(buttonText)
    }

    private fun getLegacyOption(): MuunPicker.Option {
        val title = getStyledString(R.string.address_picker_legacy_title)
        val description = getStyledString(R.string.address_picker_legacy_desc)

        val status = if (addressType == AddressType.LEGACY) Status.SELECTED else Status.NORMAL

        return MuunPicker.Option(AddressType.LEGACY.ordinal, title, description, status)
    }

    private fun getSegwitOption(): MuunPicker.Option {
        val title = getStyledString(R.string.address_picker_segwit_title)
        val description = getStyledString(R.string.address_picker_segwit_desc)

        val status = if (addressType == AddressType.SEGWIT) Status.SELECTED else Status.NORMAL

        return MuunPicker.Option(AddressType.SEGWIT.ordinal, title, description, status)
    }

    private fun getTaprootOption(): MuunPicker.Option {
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

        return MuunPicker.Option(AddressType.TAPROOT.ordinal, title, description, status)
    }

    private fun isTaprootOptionIncluded() =
        when (taprootStatus) {
            UserActivatedFeatureStatus.PREACTIVATED,
            UserActivatedFeatureStatus.SCHEDULED_ACTIVATION,
            UserActivatedFeatureStatus.ACTIVE -> true
            else -> false
        }
}