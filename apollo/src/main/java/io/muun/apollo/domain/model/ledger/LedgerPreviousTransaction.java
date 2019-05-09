package io.muun.apollo.domain.model.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LedgerPreviousTransaction {

    public String version;

    public List<LedgerTransactionInput> inputs;

    public List<LedgerTransactionOutput> outputs;

    public String locktime;

    public String witness;

    /**
     * Constructor.
     */
    public LedgerPreviousTransaction(String version,
                                     List<LedgerTransactionInput> inputs,
                                     List<LedgerTransactionOutput> outputs) {
        this.version = version;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    /**
     * Constructor.
     */
    public LedgerPreviousTransaction() {
    }

    public void setLocktime(String locktime) {
        this.locktime = locktime;
    }

    public void setWitness(String witness) {
        this.witness = witness;
    }

    @Override public String toString() {
        return "LedgerPreviousTransaction{"
                + "version='" + version + '\''
                + ", inputs=" + inputs
                + ", outputs=" + outputs
                + ", locktime='" + locktime + '\''
                + ", witness='" + witness + '\''
                + '}';
    }
}
