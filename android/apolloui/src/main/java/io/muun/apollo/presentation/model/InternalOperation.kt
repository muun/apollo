package io.muun.apollo.presentation.model

import android.content.Context
import io.muun.apollo.R
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.model.PublicProfile
import io.muun.apollo.presentation.ui.utils.LinkBuilder
import io.muun.apollo.presentation.ui.view.RichText
import io.muun.common.model.OperationDirection
import org.bitcoinj.core.NetworkParameters

class InternalOperation(
    operation: Operation,
    linkBuilder: LinkBuilder,
    currencyDisplayMode: CurrencyDisplayMode,
    context: Context
) : UiOperation(operation, linkBuilder, currencyDisplayMode, context) {

    private val contact: PublicProfile = if (operation.direction == OperationDirection.OUTGOING)
        checkNotNull(operation.receiverProfile)
    else
        checkNotNull(operation.senderProfile)

    override fun getFormattedTitle(context: Context, shortName: Boolean): CharSequence =
        if (isCyclical) {
            context.getString(R.string.operation_sent_to_yourself)
        } else {
            val formattedName = RichText(getContactName(shortName)).setBold()
            if (operation.direction == OperationDirection.INCOMING) {
                context.getString(R.string.internal_incoming_operation, formattedName)
            } else {
                context.getString(R.string.internal_outgoing_operation, formattedName)
            }
        }

    private fun getContactName(shortName: Boolean): String =
        if (shortName) "${contact.firstName} ${contact.lastName[0]}" else contact.fullName

    override fun getPictureUri(context: Context): String? {
        return contact.profilePictureUrl
    }
}