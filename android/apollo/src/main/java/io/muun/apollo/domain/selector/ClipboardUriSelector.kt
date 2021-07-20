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
    private val transformerFactory: ExecutionTransformerFactory
) {

    fun watch(): Observable<OperationUri?> =
        clipboardProvider.watchPrimaryClip()
            .compose(transformerFactory.getObservableReverseAsyncExecutor()) // sub on main required
            .map {
                if (it == userRepository.lastCopiedAddress) {
                    null // don't show the user the address or invoice she just generated

                } else try {
                    OperationUri.fromString(it)

                } catch (ex: IllegalArgumentException) {
                    null
                }
            }

    fun get() =
        watch().toBlocking().first()
}