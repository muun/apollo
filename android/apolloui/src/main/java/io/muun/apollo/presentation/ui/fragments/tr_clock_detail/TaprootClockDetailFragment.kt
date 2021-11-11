package io.muun.apollo.presentation.ui.fragments.tr_clock_detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.hasAppInstalled
import io.muun.apollo.presentation.ui.utils.setStyledText
import io.muun.apollo.presentation.ui.view.BlockClock
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import timber.log.Timber


class TaprootClockDetailFragment: SingleFragment<TaprootClockDetailPresenter>(), TaprootClockDetailView {

    @BindView(R.id.block_clock)
    lateinit var blockClock: BlockClock

    @BindView(R.id.description)
    lateinit var descriptionView: TextView


    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        super.initializeUi(view)
        parentActivity.header.setNavigation(Navigation.BACK)
        parentActivity.header.hideTitle()
        parentActivity.header.setElevated(false)

        blockClock.setOnClickListener {

            // EASTER EGG TIME!!!

            try {
                finalCountdownEasterEgg()
            } catch (e: Throwable) {
                Timber.e("There was a problem trying to show our easter egg :(")
            }
        }
    }

    private fun finalCountdownEasterEgg() {
        val context = requireContext()
        val spotifyIntent = Intent(Intent.ACTION_VIEW)
        spotifyIntent.data = Uri.parse("spotify:track/3MrRksHupTVEQ7YbA0FsZK?si=123efa0a9d6d4e7c#0:13")
        spotifyIntent.putExtra(
            Intent.EXTRA_REFERRER,
            Uri.parse("android-app://" + context.applicationContext.packageName)
        )

        if (context.packageManager.hasAppInstalled(spotifyIntent)) {
            try {
                context.startActivity(spotifyIntent)
                presenter.reportEasterEgg("spotify")
                return
            } catch (ex: ActivityNotFoundException) {
                // DO nothing. We fallback to youtube
            }
        }

        val youtubeAppIntent = Intent(Intent.ACTION_VIEW)
        youtubeAppIntent.data = Uri.parse("vnd.youtube:9jK-NcRmVcw?t=14")

        val webIntent = Intent(Intent.ACTION_VIEW)
        webIntent.data = Uri.parse("https://www.youtube.com/watch?v=9jK-NcRmVcw?t=14")

        try {
            startActivity(youtubeAppIntent)
            presenter.reportEasterEgg("youtube")
        } catch (ex: ActivityNotFoundException) {
            startActivity(webIntent)
            presenter.reportEasterEgg("youtube-web")
        }
    }

    override fun getLayoutResource() =
        R.layout.fragment_tr_clock_detail

    override fun setTaprootCounter(blocksRemaining: Int) {
        blockClock.value = blocksRemaining
        descriptionView.setStyledText(R.string.tr_clock_detail_desc, blocksRemaining)
    }

    override fun onBackPressed(): Boolean {
        finishActivity()
        return true
    }
}