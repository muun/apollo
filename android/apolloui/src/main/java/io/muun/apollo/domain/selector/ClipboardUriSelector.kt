package io.muun.apollo.domain.selector

import io.muun.apollo.data.os.ClipboardProvider
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.OperationUri
import rx.Observable
import javax.inject.Inject


/**
 * This component CAN'T be injected into any component that can be initialized in background
 * (e.g MuunWorkerFactory, NotificationProcessor, etc...), as it depends ClipboardProvider which
 * depends on a system call that can only be made from the Main thread.
 */
class ClipboardUriSelector @Inject constructor(
    private val clipboardProvider: ClipboardProvider,
    private val userRepository: UserRepository,
    private val transformerFactory: ExecutionTransformerFactory,
) {

    @Deprecated(
        "Starting Android 12 (api 31) special care needs to be taken when accessing Clipboard." +
            "Constantly checking the clipboard is no longer an accepted/ux-friendly practice."
    )
    fun watch(): Observable<OperationUri?> =
        clipboardProvider.watchPrimaryClip()
            .compose(transformerFactory.getObservableReverseAsyncExecutor()) // sub on main required
            .map {
                if (it == userRepository.lastCopiedContentFromReceive) {
                    null // don't show the user the address or invoice she just generated

                } else try {
                    OperationUri.fromString(it)

                } catch (ex: IllegalArgumentException) {
                    null
                }
            }

    /**
     * Starting Android 12 (api 31) special care needs to be taken when accessing Clipboard.
     * Constantly checking the clipboard is no longer an accepted/ux-friendly practice (e.g OS shows
     * a toast message with the legend 'APP pasted from your clipboard'). Callers are responsible of
     * calling this method in a UX-friendly manner.
     */
    fun getText(): String =
        clipboardProvider.paste()?.trim() ?: ""

    /**
     * Checks whether a certain content is exactly equal to what the user last copied to the
     * clipboard in our Receive screen.
     */
    fun isLastCopiedFromReceive(content: String): Boolean =
        content == userRepository.lastCopiedContentFromReceive
}