package io.muun.apollo.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

public class SubmarineSwapReceiver {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Nullable
    public final String alias;
    public final String serializedNetworkAddresses;
    public final String publicKey;

    /**
     * Constructor.
     */
    public SubmarineSwapReceiver(
            @Nullable String alias,
            String serializedNetworkAddresses,
            String publicKey) {

        this.alias = alias;
        this.serializedNetworkAddresses = serializedNetworkAddresses;
        this.publicKey = publicKey;
    }

    public String getFormattedDestination() {
        return publicKey + "@" + getDisplayNetworkAddress();
    }

    public String getDisplayNetworkAddress() {
        final List<String> networkAddresses = getNetworkAddresses();
        return networkAddresses.isEmpty() ? "" : networkAddresses.get(0);
    }

    /**
     * Get the list of network addresses, ie. the concatenation of "{host}:{port}". Might be empty!
     */
    private List<String> getNetworkAddresses() {

        try {
            return Arrays.asList(JSON_MAPPER.readValue(serializedNetworkAddresses, String[].class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
