package io.muun.apollo.presentation.ui.fragments.ek_intro

import androidx.fragment.app.FragmentManager
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroPager

class EmergencyKitIntroPager(fm: FragmentManager) : FlowIntroPager(fm) {

    override fun createPageList() = listOf(
        Page(
            R.drawable.one_document,
            R.string.export_keys_intro_1_title,
            R.string.export_keys_intro_1_body
        ),

        Page(
            R.drawable.stored_online,
            R.string.export_keys_intro_2_title,
            R.string.export_keys_intro_2_body
        ),

        Page(
            R.drawable.complete_ownership,
            R.string.export_keys_intro_3_title,
            R.string.export_keys_intro_3_body
        ))

}