package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.MuunSwitchSettingItemBinding

class MuunSwitchSettingItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    style: Int = 0,
) : MuunView(context, attrs, style) {

    companion object {
        val viewProps: ViewProps<MuunSwitchSettingItem> = ViewProps.Builder<MuunSwitchSettingItem>()
            .addStringJava(R.attr.label, MuunSwitchSettingItem::setLabel)
            .addBoolean(R.attr.checked, MuunSwitchSettingItem::setChecked)
            .build()
    }

    private val binding: MuunSwitchSettingItemBinding
        get() = getBinding() as MuunSwitchSettingItemBinding

    override val layoutResource: Int
        get() = R.layout.muun_switch_setting_item

    override fun viewBinder(): ((View) -> ViewBinding) {
        return MuunSwitchSettingItemBinding::bind
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
