package io.muun.apollo.domain.selector

import io.muun.apollo.data.os.ClipboardProvider
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.OperationUri
import rx.Observable
import javax.inject.Inject


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
                    null // don't show the user the address he just generated

                } else try {
                    OperationUri.fromString(it)

                } catch (ex: IllegalArgumentException) {
                    null
                }
            }

    fun get() =
        watch().toBlocking().first()
}