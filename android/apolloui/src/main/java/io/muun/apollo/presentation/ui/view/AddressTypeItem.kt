package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.show_qr.bitcoin.AddressType
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.common.exception.MissingCaseError

class AddressTypeItem @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView(c, a, s) {

    companion object {
        const val REQUEST_ADDRESS_TYPE = 1

        const val SEGWIT_OPTION = 1
        const val LEGACY_OPTION = 2
    }

    interface AddresTypeChangedListener {
        fun onAddressTypeChanged(newType: AddressType)
    }

    private lateinit var listener: AddresTypeChangedListener

    @BindView(R.id.edit_address_type)
    lateinit var editAddressTypeButton: TextView

    @State
    lateinit var addressType: AddressType

    override fun getLayoutResource(): Int {
        return R.layout.edit_address_type_item
    }

    fun show(addressType: AddressType) {
        this.addressType = addressType
        when(addressType) {
            AddressType.SEGWIT -> editAddressTypeButton.setText(R.string.segwit)
            AddressType.LEGACY -> editAddressTypeButton.setText(R.string.legacy)
        }
    }

    fun setOnAddressTypeChangedListener(listener: AddresTypeChangedListener) {
        this.listener = listener;
    }

    @OnClick(R.id.address_type_label)
    fun onLabelClick() {

        val dialog = TitleAndDescriptionDrawer()

        StyledStringRes(context, R.string.bitcoin_address_help_content)
            .toCharSequence()
            .let(dialog::setDescription)

        showDrawerDialog(dialog)
    }

    @OnClick(R.id.edit_address_type)
    fun onAddressTypeClick() {

        val dialog = PickerDialogFragment()

        dialog.setTitle(R.string.choose_address_type)

        val segwitSelected = addressType == AddressType.SEGWIT
        dialog.addOption(SEGWIT_OPTION, context.getString(R.string.segwit), segwitSelected)
        dialog.addOption(LEGACY_OPTION, context.getString(R.string.legacy), !segwitSelected)

        requestExternalResult(REQUEST_ADDRESS_TYPE, dialog)
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when (requestCode) {
            REQUEST_ADDRESS_TYPE -> handleNewAddressType(resultCode)

            else -> {
                // Ignore
            }
        }
    }

    private fun handleNewAddressType(resultCode: Int) {
        when (resultCode) {
            SEGWIT_OPTION -> {
                editAddressTypeButton.text = context.getString(R.string.segwit)
                listener.onAddressTypeChanged(AddressType.SEGWIT)
            }

            LEGACY_OPTION -> {
                editAddressTypeButton.text = context.getString(R.string.legacy)
                listener.onAddressTypeChanged(AddressType.LEGACY)
            }

            else ->
                throw MissingCaseError(resultCode, "AddressType picker")
        }
    }
}