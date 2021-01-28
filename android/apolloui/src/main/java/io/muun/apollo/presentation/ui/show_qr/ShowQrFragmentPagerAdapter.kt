package io.muun.apollo.presentation.ui.show_qr

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import io.muun.apollo.presentation.ui.show_qr.bitcoin.BitcoinAddressQrFragment
import io.muun.apollo.presentation.ui.show_qr.ln.LnInvoiceQrFragment

class ShowQrFragmentPagerAdapter(
    private val fm: FragmentManager,
    private val context: Context
) : FragmentPagerAdapter(fm) {

    // This is what we need to do in order to re-generate ln invoice each time the user swipes back
    // to the Ln Invoice tab. It ain't pretty but is 100% reliable.
    @JvmField
    val onPageChangeListener: OnPageChangeListener = object : OnPageChangeListener {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPxs: Int) {}

        override fun onPageSelected(position: Int) {
            val fragment = getExistingItem(position)

            if (fragment is LnInvoiceQrFragment) {
                fragment.refresh()
            }
        }

        override fun onPageScrollStateChanged(state: Int) {}
    }

    override fun getCount(): Int =
        ShowQrPage.values().size

    /**
     * Return a newly created Fragment. Called by the ViewPager, exactly once for each position,
     * all when the ViewPager itself is first attached (ie not during navigation or recreation).
     */
    override fun getItem(position: Int): Fragment {

        return when (ShowQrPage.at(position)) {
            ShowQrPage.BITCOIN -> BitcoinAddressQrFragment()
            ShowQrPage.LN -> LnInvoiceQrFragment()
        }
    }

    /**
     * Return an existing Fragment in the ViewPager. This will succeed after the ViewPager is first
     * attached, even across recreations, since the FragmentManager preserves state.
     */
    private fun getExistingItem(position: Int): Fragment {
        return fm.fragments.find { ShowQrPage.classAt(position).isInstance(it) }!!
    }


    override fun getPageTitle(position: Int): CharSequence? {
        return context.resources.getString(ShowQrPage.at(position).titleRes)
            .toUpperCase()
    }
}