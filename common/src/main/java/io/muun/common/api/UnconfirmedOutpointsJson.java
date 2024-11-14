package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.validation.constraints.NotNull;

/**
 * It contains the unconfirmed UTXOs that will be used to obtain the
 * corresponding fee bump functions from realtime/fees API. The order
 * passed here matches the order of the returned functions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnconfirmedOutpointsJson {

    @NotNull
    public List<String> unconfirmedOutpoints;

    public UnconfirmedOutpointsJson(List<String> unconfirmedOutpoints) {
        this.unconfirmedOutpoints = unconfirmedOutpoints;
    }

    /**
     * Return the list of unconfirmed outpoints separated by ','.
     */
    @Override
    public String toString() {
        return String.join(", ", unconfirmedOutpoints);
    }

}
