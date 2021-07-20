package io.muun.apollo.domain

import io.muun.apollo.data.os.ClipboardProvider
import io.muun.apollo.data.preferences.UserRepository
import javax.inject.Inject

/**
 * This component CAN'T be injected into any component that can be initialized in background
 * (e.g MuunWorkerFactory, NotificationProcessor, etc...), as it depends ClipboardProvider which
 * depends on a system call that can only be made from the Main thread.
 */
class ClipboardManager @Inject constructor(
    private val clipboardProvider: ClipboardProvider,
    private val userRepository: UserRepository
) {

    /**
     * Copy some text to the clipboard.
     *
     * @param label a nullable, NON user-visible label used mainly to identify the clip data.
     * @param text The actual text to copy, or null to clear clipboard.
     */
    fun copy(label: String?, text: String?) =
        clipboardProvider.copy(label, text)

    /**
     * Copy an external address or LN invoice to the system clipboard.
     */
    fun copyQrContent(qrContent: String) {
        copy("Bitcoin address/Ln invoice", qrContent)
        userRepository.lastCopiedAddress = qrContent
    }
}