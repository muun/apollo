package io.muun.apollo.presentation.app

import android.content.Context
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiCompat.MetadataRepoLoader
import androidx.emoji2.text.EmojiCompat.MetadataRepoLoaderCallback
import androidx.emoji2.text.MetadataRepo
import io.muun.common.utils.Preconditions

/**
 * [EmojiCompat.Config] implementation that loads the metadata using AssetManager and
 * bundled resources.
 *
 *
 * <pre>`EmojiCompat.init(new BundledEmojiCompatConfig(context));`</pre>
 *
 * Including the emoji2-bundled artifact disables the
 * [androidx.emoji2.text.EmojiCompatInitializer]. You must manually call EmojiCompat.init
 * when using the bundled configuration.
 *
 * @see EmojiCompat
 */
class BundledEmojiCompatConfig(ctx: Context) : EmojiCompat.Config(BundledMetadataLoader(ctx)) {

    private class BundledMetadataLoader(context: Context) : MetadataRepoLoader {

        private val mContext: Context

        override fun load(loaderCallback: MetadataRepoLoaderCallback) {
            Preconditions.checkNotNull(loaderCallback, "loaderCallback cannot be null")
            val runnable = InitRunnable(mContext, loaderCallback)
            val thread = Thread(runnable)
            thread.isDaemon = false
            thread.start()
        }

        init {
            mContext = context.applicationContext
        }
    }

    private class InitRunnable(
        private val mContext: Context,
        private val mLoaderCallback: MetadataRepoLoaderCallback,
    ) : Runnable {

        companion object {
            private const val FONT_NAME = "NotoColorEmoji-noflags.ttf"
        }

        override fun run() {
            try {
                val assetManager = mContext.assets
                val resourceIndex = MetadataRepo.create(assetManager, FONT_NAME)

                // BEWARE of inspecting resourceIndex.emojiCharArray. Upon inspection, emojis seem
                // to stop working (e.g they don't get rendered/decoded correctly).

                mLoaderCallback.onLoaded(resourceIndex)
            } catch (t: Throwable) {
                mLoaderCallback.onFailed(t)
            }
        }
    }
}