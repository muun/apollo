package io.muun.apollo.presentation.ui.select_night_mode

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivitySelectNightModeBinding
import io.muun.apollo.domain.model.NightMode
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.utils.OS
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.utils.isInNightMode
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.apollo.presentation.ui.view.MuunSettingItem

class SelectNightModeActivity : BaseActivity<SelectNightModePresenter>(), SelectNightModeView {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, SelectNightModeActivity::class.java)
    }

    private val binding: ActivitySelectNightModeBinding
        get() = getBinding() as ActivitySelectNightModeBinding

    private val header: MuunHeader
        get() = binding.selectDarkModeHeader


    private val darkModeItem: MuunSettingItem
        get() = binding.nightModeDark


    private val lightModeItem: MuunSettingItem
        get() = binding.nightModeLight

    private val followSystemItem: MuunSettingItem
        get() = binding.nightModeFollowSystem

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.activity_select_night_mode

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivitySelectNightModeBinding::inflate
    }

    override fun initializeUi() {
        super.initializeUi()

        header.let {
            it.attachToActivity(this)
            it.showTitle(R.string.dark_mode)
            it.setNavigation(Navigation.BACK)
        }

        darkModeItem.setOnClickListener { onItemSelected(NightMode.DARK) }
        lightModeItem.setOnClickListener { onItemSelected(NightMode.LIGHT) }
        followSystemItem.setOnClickListener { onItemSelected(NightMode.FOLLOW_SYSTEM) }

        if (!OS.supportsDarkMode()) {
            followSystemItem.visibility = View.GONE
        }
    }

    override fun setNightMode(nightMode: NightMode) {
        val selectedIcon = UiUtils.getTintedDrawable(
            this,
            R.drawable.ic_check_black_24_px,
            R.color.blue
        )

        darkModeItem.setIcon(null)
        lightModeItem.setIcon(null)
        followSystemItem.setIcon(null)

        when (nightMode) {
            NightMode.DARK -> {
                darkModeItem.setIcon(selectedIcon)
            }

            NightMode.LIGHT -> {
                lightModeItem.setIcon(selectedIcon)
            }

            NightMode.FOLLOW_SYSTEM -> {
                followSystemItem.setIcon(selectedIcon)

                if (!OS.supportsDarkMode()) {
                    if (isInNightMode()) {
                        setNightMode(NightMode.DARK)

                    } else {
                        setNightMode(NightMode.LIGHT)

                    }
                }
            }
        }
    }

    private fun onItemSelected(newNightMode: NightMode) {
        when (newNightMode) {

            NightMode.DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                presenter.reportNightModeChange(NightMode.DARK)
            }

            NightMode.LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                presenter.reportNightModeChange(NightMode.LIGHT)
            }

            NightMode.FOLLOW_SYSTEM -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                presenter.reportNightModeChange(NightMode.FOLLOW_SYSTEM)
            }
        }
    }
}