package io.muun.apollo.domain.action.session

import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.user.EmailLinkAction
import io.muun.apollo.domain.errors.InvalidActionLinkError
import io.muun.apollo.domain.utils.UriParser
import io.muun.common.exception.MissingCaseError
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@VisibleForTesting // open (non-final) class so mockito can mock/spy
open class UseMuunLinkAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val emailLinkAction: EmailLinkAction
): BaseAsyncAction1<String, Void>() {

    private val LINK_PARAM_UUID = "uuid"

    override fun action(linkUri: String): Observable<Void> =
        Observable.defer { handleLink(linkUri) }


    private fun handleLink(linkUri: String): Observable<Void> {
        val parser = UriParser(linkUri)

        if (!shouldHandleLink(parser)) {
            return Observable.just(null) // not our business
        }

        if (parser.pathWithSlash != emailLinkAction.getPending()) {
            throw InvalidActionLinkError(linkUri, emailLinkAction.getPending())
        }

        val uuid = parser.getParam(LINK_PARAM_UUID)

        return when {
            isEmailVerify(parser) -> houstonClient.useVerifyLink(uuid)
            isEmailAuthorize(parser) -> houstonClient.useAuthorizeLink(uuid)
            isEmailConfirm(parser) -> houstonClient.useConfirmLink(uuid)
            isRcLoginAuthorize(parser) -> houstonClient.authorizeLoginWithRecoveryCode(uuid)
            isAccountDeletionConfirm(parser) -> houstonClient.confirmAccountDeletion(uuid)

            else ->
                throw MissingCaseError(linkUri, "Muun links")
        }
    }

    private fun shouldHandleLink(p: UriParser) =
        p.host == Globals.INSTANCE.muunLinkHost

    private fun isEmailVerify(p: UriParser) =
        p.pathWithSlash == Globals.INSTANCE.verifyLinkPath

    private fun isEmailAuthorize(p: UriParser) =
        p.pathWithSlash == Globals.INSTANCE.authorizeLinkPath

    private fun isEmailConfirm(p: UriParser) =
        p.pathWithSlash == Globals.INSTANCE.confirmLinkPath

    private fun isRcLoginAuthorize(p: UriParser) =
        p.pathWithSlash == Globals.INSTANCE.rcLoginAuthorizePath

    private fun isAccountDeletionConfirm(p: UriParser) =
        p.pathWithSlash == Globals.INSTANCE.confirmAccountDeletionPath
}