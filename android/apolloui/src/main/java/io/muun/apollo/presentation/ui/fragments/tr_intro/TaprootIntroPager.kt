package io.muun.apollo.presentation.ui.fragments.tr_intro

import androidx.fragment.app.FragmentManager
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroPager

class TaprootIntroPager(fm: FragmentManager) : FlowIntroPager(fm) {

    companion object {
        val PAGES = listOf(
            Page(
                R.drawable.taproot_astronaut,
                R.string.tr_setup_intro_1_title,
                R.string.tr_setup_intro_1_desc
            ),

            Page(
                R.drawable.taproot_musig,
                R.string.tr_setup_intro_2_title,
                R.string.tr_setup_intro_2_desc
            ),

            Page(
                R.drawable.taproot_safe,
                R.string.tr_setup_intro_3_title,
                R.string.tr_setup_intro_3_desc
            )
        )
    }

    override fun createPageList() =
        PAGES
}