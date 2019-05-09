package io.muun.apollo.domain.model.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LedgerTransaction {

    public List<List<Object>> inputs;

    public List<String> associatedKeysets;

    public String changePath;

    public boolean segwit;

    public String outputScriptHex;

    /**
     * Constructor.
     */
    public LedgerTransaction(List<List<Object>> inputs,
                             List<String> associatedKeysets,
                             String changePath,
                             boolean segwit,
                             String outputScriptHex) {
        this.inputs = inputs;
        this.associatedKeysets = associatedKeysets;
        this.changePath = changePath;
        this.segwit = segwit;
        this.outputScriptHex = outputScriptHex;
    }

    /**
     * Json Constructor.
     */
    public LedgerTransaction() {
    }


}
