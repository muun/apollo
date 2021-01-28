package io.muun.common.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LnInvoiceTestData {

    public String description;

    public String request;

    public Values expected;

    /**
     * Json constructor.
     */
    public LnInvoiceTestData() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Values {

        @JsonProperty("chain_addresses")
        public List<String> chainAddresses;

        @JsonProperty("cltv_delta")
        public long cltvDelta;

        @JsonProperty("created_at")
        public String createdAt;

        public String description;

        @JsonProperty("description_hash")
        public String descriptionHash;

        public String destination;

        @JsonProperty("expires_at")
        public String expiresAt;

        public String id;

        @JsonProperty("is_expired")
        public boolean isExpired;

        public String network;

        @JsonProperty("mtokens")
        public String amountWithMillis;

        @JsonProperty("tokens")
        public long amountInSatoshis;

        /**
         * Json constructor.
         */
        public Values() {
        }
    }
}
