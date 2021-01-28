package io.muun.apollo.presentation.model;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.presentation.ui.utils.LinkBuilder;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.common.model.OperationDirection;

import android.content.Context;
import org.bitcoinj.core.NetworkParameters;

import javax.annotation.Nullable;

import static io.muun.common.utils.Preconditions.checkNotNull;

public class InternalOperation extends UiOperation {

    private final PublicProfile contact;

    /**
     * Constructor.
     */
    public InternalOperation(Operation operation,
                             NetworkParameters networkParameters,
                             LinkBuilder linkBuilder,
                             CurrencyDisplayMode currencyDisplayMode) {

        super(operation, networkParameters, linkBuilder, currencyDisplayMode);

        contact = operation.direction == OperationDirection.OUTGOING
                ? operation.receiverProfile
                : operation.senderProfile;

        checkNotNull(contact);
    }

    @Override
    public CharSequence getFormattedTitle(Context context, boolean useShortName) {
        if (isCyclical()) {
            return context.getString(R.string.operation_sent_to_yourself);

        } else {
            final RichText formattedName = new RichText(getContactName(useShortName)).setBold();

            if (operation.direction == OperationDirection.INCOMING) {
                return context.getString(R.string.internal_incoming_operation, formattedName);


            } else {
                return context.getString(R.string.internal_outgoing_operation, formattedName);
            }
        }
    }

    private String getContactName(boolean shortName) {
        return shortName
                ? String.format("%s %c.", contact.firstName, contact.lastName.charAt(0))
                : contact.getFullName();
    }

    @Override
    @Nullable
    public String getPictureUri(Context context) {

        return contact.profilePictureUrl;
    }
}
