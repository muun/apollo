package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

/**
 * It contains the unconfirmed UTXOs that will be used to obtain the
 * corresponding fee bump functions from realtime/fees API. The order
 * passed here matches the order of the returned functions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RealTimeFeesRequestJson {

    @NotNull
    public List<String> unconfirmedOutpoints;

    // Indicates the call point from mobile for which the fee bump functions should be updated.
    @Nullable
    public FeeBumpRefreshPolicy feeBumpRefreshPolicy;

    public RealTimeFeesRequestJson(
            List<String> unconfirmedOutpoints,
            FeeBumpRefreshPolicy refreshPolicy
    ) {
        this.unconfirmedOutpoints = unconfirmedOutpoints;
        this.feeBumpRefreshPolicy = refreshPolicy;
    }

    /**
     * JSON constructor.
     */
    public RealTimeFeesRequestJson() {
    }

    public enum FeeBumpRefreshPolicy {
        FOREGROUND("foreground"),
        PERIODIC("periodic"),
        NEW_OPERATION("newOpBlockingly"),
        CHANGED_NEXT_TRANSACTION_SIZE("changedNts"),
        UNKNOWN("unknown");

        private final String mobileName;

        FeeBumpRefreshPolicy(String mobileName) {
            this.mobileName = mobileName;
        }

        @JsonCreator
        public static FeeBumpRefreshPolicy fromValue(String value) {
            for (FeeBumpRefreshPolicy refreshPolicy : values()) {
                if (refreshPolicy.mobileName.equals(value)) {
                    return refreshPolicy;
                }
            }
            return UNKNOWN;

        }
    }
}
