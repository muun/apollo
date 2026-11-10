package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import io.muun.apollo.R
import io.muun.apollo.databinding.MuunSwitchSettingItemBinding

class MuunSwitchSettingItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    style: Int = 0,
) : MuunView<MuunSwitchSettingItemBinding>(context, attrs, style) {

    companion object {
        val viewProps: ViewProps<MuunSwitchSettingItem> = ViewProps.Builder<MuunSwitchSettingItem>()
            .addStringJava(R.attr.label, MuunSwitchSettingItem::setLabel)
            .addBoolean(R.attr.checked, MuunSwitchSettingItem::setChecked)
            .build()
    }

    override fun bindingInflater(): BindingInflater<MuunSwitchSettingItemBinding> {
        return MuunSwitchSettingItemBinding::inflate
    }

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)
        viewProps.transfer(attrs, this)
    }

    fun setLabel(labelText: CharSequence?) {
        binding.settingItemLabel.text = labelText
    }

    fun setChecked(checked: Boolean) {
        binding.settingItemSwitch.isChecked = checked
    }

    fun setOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
        binding.settingItemSwitch.setOnCheckedChangeListener(listener)
    }
}
