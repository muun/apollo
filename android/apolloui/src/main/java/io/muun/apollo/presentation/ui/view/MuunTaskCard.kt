package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import butterknife.BindView
import io.muun.apollo.R

class MuunTaskCard @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    enum class Status {
        DEFAULT,    // no decorations or indicators
        INACTIVE,   // looks disabled
        ACTIVE,     // looks clickable
        DONE,       // looks completed and cheerful. Congratulations.
        SKIPPED     // looks skipped, haha. Kinda disabled, but clickable and with a SKIPPED tag.
                    // Only for EMAIL CARD.
    }

    @BindView(R.id.icon)
    internal lateinit var iconView: ImageView

    @BindView(R.id.title)
    internal lateinit var titleView: TextView

    @BindView(R.id.body)
    internal lateinit var bodyView: TextView

    override fun getLayoutResource() =
        R.layout.muun_task_card

    var icon: Drawable
        get() = iconView.drawable
        set(drawable) {
            iconView.setImageDrawable(drawable)
        }

    var title: CharSequence
        get() = titleView.text
        set(text) {
            titleView.text = text
        }

    var body: CharSequence
        get() = bodyView.text
        set(text) {
            bodyView.text = text
        }

    var status: Status = Status.DEFAULT
        set(newStatus) {
            val bgRes = when (newStatus) {
                Status.DEFAULT -> R.drawable.muun_task_card_default_bg
                Status.INACTIVE -> R.drawable.muun_task_card_inactive_bg
                Status.ACTIVE -> R.drawable.muun_task_card_active_bg
                Status.DONE -> R.drawable.muun_task_card_done_bg
                Status.SKIPPED -> R.drawable.muun_task_card_skipped_bg
            }

            val titleColorRes = ContextCompat.getColor(context, when (newStatus) {
                Status.DEFAULT -> R.color.muun_blue
                Status.ACTIVE -> R.color.muun_blue
                Status.INACTIVE -> R.color.muun_gray_light
                Status.DONE -> R.color.muun_gray_dark
                Status.SKIPPED -> R.color.muun_blue
            })

            val bodyColorRes = ContextCompat.getColor(context, when (newStatus) {
                Status.DEFAULT -> R.color.muun_gray_dark
                Status.ACTIVE -> R.color.muun_gray_dark
                Status.INACTIVE -> R.color.muun_gray_light
                Status.DONE -> R.color.muun_gray_dark
                Status.SKIPPED -> R.color.muun_gray_dark
            })

            setBackgroundResource(bgRes)
            titleView.setTextColor(titleColorRes)
            bodyView.setTextColor(bodyColorRes)

            isClickable = (newStatus == Status.DEFAULT
                || newStatus == Status.ACTIVE
                || newStatus == Status.SKIPPED)

            field = newStatus
        }
}