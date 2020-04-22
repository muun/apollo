package io.muun.common.api.messages;

import io.muun.common.api.PartiallySignedTransactionJson;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReSignOperationMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "operations/resign",
            SessionStatus.LOGGED_IN,
            MessageOrigin.HOUSTON
    );

    @NotNull
    public Long operationId;

    // The replacement proof can be used to verify that the new transaction is a legitimate
    // replacement of a transaction already signed in the past.
    @NotNull
    public String replacementProofInBase64;

    @NotNull
    public PartiallySignedTransactionJson transaction;

    /**
     * Json constructor.
     */
    public ReSignOperationMessage() {
    }

    /**
     * Houston constructor.
     */
    public ReSignOperationMessage(
            long operationId,
            String replacementProofInBase64,
            PartiallySignedTransactionJson transaction) {

        this.operationId = operationId;
        this.replacementProofInBase64 = replacementProofInBase64;
        this.transaction = transaction;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
