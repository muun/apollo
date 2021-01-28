package io.muun.apollo.presentation.ui.fragments.ek_intro

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.fragments.explanation_block.ExplanationPageFragment
import io.muun.common.exception.MissingCaseError

class EmergencyKitIntroPager(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {

    override fun getCount() = 3

    override fun getItem(position: Int) =
        when (position) {
            0 -> ExplanationPageFragment.create(
                R.drawable.one_document,
                R.string.export_keys_intro_1_title,
                R.string.export_keys_intro_1_body
            )

            1 -> ExplanationPageFragment.create(
                R.drawable.stored_online,
                R.string.export_keys_intro_2_title,
                R.string.export_keys_intro_2_body
            )

            2 -> ExplanationPageFragment.create(
                R.drawable.safe_v2,
                R.string.export_keys_intro_3_title,
                R.string.export_keys_intro_3_body
            )

            else ->
                throw MissingCaseError(position, "ExportKeysIntroPager index")
        }

}