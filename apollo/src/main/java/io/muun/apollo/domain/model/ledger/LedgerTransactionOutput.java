package io.muun.apollo.domain.model.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LedgerTransactionOutput {

    public String amount;

    public String script;

    /**
     * Constructor.
     */
    public LedgerTransactionOutput(String amount, String script) {
        this.amount = amount;
        this.script = script;
    }

    /**
     * Json constructor.
     */
    public LedgerTransactionOutput() {
    }

    @Override public String toString() {
        return "LedgerTransactionOutput{"
                + "amount='" + Long.decode("0x" + amount) + '\''
                + ", script='" + script + '\''
                + '}';
    }
}
