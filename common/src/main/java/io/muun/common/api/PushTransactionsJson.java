package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PushTransactionsJson {

    @Nullable
    public RawTransaction rawTransaction;

    public List<RawTransaction> alternativeTransactions;

    /**
     * Json constructor.
     */
    public PushTransactionsJson() {
    }

    /**
     * Apollo constructor.
     */
    public PushTransactionsJson(
            @Nullable RawTransaction rawTransaction,
            List<RawTransaction> alternativeTransactions
    ) {
        this.rawTransaction = rawTransaction;
        this.alternativeTransactions = alternativeTransactions;
    }
}
