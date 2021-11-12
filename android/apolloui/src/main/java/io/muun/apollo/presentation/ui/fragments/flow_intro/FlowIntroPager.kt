package io.muun.apollo.presentation.ui.fragments.flow_intro

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import io.muun.apollo.presentation.ui.fragments.explanation_block.ExplanationPageFragment

abstract class FlowIntroPager(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {

    class Page(
        @DrawableRes val imageRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int
    )

    private val pages by lazy { createPageList() }

    protected abstract fun createPageList(): List<Page>

    override fun getCount() =
        pages.size

    override fun getItem(position: Int) =
        pages[position].let {
            ExplanationPageFragment.create(it.imageRes, it.titleRes, it.bodyRes)
        }
}