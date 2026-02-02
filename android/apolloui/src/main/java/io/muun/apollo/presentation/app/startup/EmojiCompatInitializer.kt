package io.muun.apollo.presentation.app.startup

import android.content.Context
import androidx.emoji2.text.EmojiCompat
import io.muun.apollo.presentation.app.BundledEmojiCompatConfig
import rx.Completable
import rx.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class EmojiCompatInitializer @Inject constructor(
    private val applicationContext: Context,
) : Initializer {

    override fun init(): Completable = Completable.fromEmitter { emitter ->
        Timber.d("[AppStartup] Running EmojiCompatAppStartupInitializer...")
        EmojiCompat.init(BundledEmojiCompatConfig(applicationContext))
        Timber.d("[AppStartup] Running EmojiCompatAppStartupInitializer DONE")
        emitter.onCompleted()
    }.subscribeOn(Schedulers.computation())
}
