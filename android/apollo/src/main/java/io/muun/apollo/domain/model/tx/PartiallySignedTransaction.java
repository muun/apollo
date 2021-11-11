package io.muun.apollo.domain.model.tx;

import io.muun.common.api.MuunInputJson;
import io.muun.common.api.PartiallySignedTransactionJson;
import io.muun.common.crypto.hd.MuunInput;
import io.muun.common.utils.Encodings;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;

/**
 * Duplicate of (at the moment of writing) Common's PartiallySignedTransaction. This is done to
 * decouple Apollo from complex PartiallySignedTransaction and TransactionScheme logic (which it
 * does not really needs). Several other reasons, not a minor one being that those classes are
 * now depending on Taproot (native C) libs which may attempt against Apollo reproducible builds.
 */
public class PartiallySignedTransaction {

    @NotNull
    private final Transaction transaction;

    @NotNull
    private final List<MuunInput> inputs;

    /**
     * Build from a json-serializable representation.
     */
    public static PartiallySignedTransaction fromJson(
            PartiallySignedTransactionJson json,
            NetworkParameters network
    ) {

        if (json == null) {
            return null;
        }

        final ArrayList<MuunInput> inputs = new ArrayList<>();
        for (MuunInputJson input : json.inputs) {
            inputs.add(MuunInput.fromJson(input));
        }

        return new PartiallySignedTransaction(
                new Transaction(network, Encodings.hexToBytes(json.hexTransaction)),
                inputs
        );
    }

    /**
     * Constructor.
     */
    public PartiallySignedTransaction(Transaction transaction, List<MuunInput> inputs) {
        this.transaction = transaction;
        this.inputs = inputs;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public List<MuunInput> getInputs() {
        return inputs;
    }

}
