package io.muun.apollo.domain.model.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LedgerTransactionInput {

    // Previous transaction hash.
    public String prevout;

    public String script;

    public String sequence;

    /**
     * Constructor.
     */
    public LedgerTransactionInput(String prevout, String script, String sequence) {
        this.prevout = prevout;
        this.script = script;
        this.sequence = sequence;
    }

    /**
     * Json constructor.
     */
    public LedgerTransactionInput() {
    }

    @Override public String toString() {
        return "LedgerTransactionInput{"
                + "prevout='" + prevout + '\''
                + ", script='" + script + '\''
                + ", sequence='" + sequence + '\''
                + '}';
    }
}
