package io.muun.apollo.domain.model;

import io.muun.apollo.BuildConfig;
import io.muun.apollo.domain.utils.UriBuilder;
import io.muun.apollo.domain.utils.UriParser;
import io.muun.common.Optional;
import io.muun.common.bitcoinj.NetworkParametersHelper;
import io.muun.common.bitcoinj.ValidationHelpers;

import android.net.Uri;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

public class OperationUri {

    private static final NetworkParameters netParams = NetworkParametersHelper
            .getNetworkParametersFromName(BuildConfig.NETWORK_NAME);

    private static final String BIP72_PAYREQ_PARAM = "r";

    public static final String MUUN_SCHEME = "muun";
    public static final String BITCOIN_SCHEME = "bitcoin";

    public static final String MUUN_HOST_CONTACT = "contacts";
    public static final String MUUN_HOST_EXTERNAL = "external";

    public static final String MUUN_AMOUNT = "amount";
    public static final String MUUN_CURRENCY = "currency";
    public static final String MUUN_DESCRIPTION = "description";

    /**
     * Createn an OperationUri from any input String, by trying to use the other factory methods.
     * Supports plain addresses, Bitcoin URIs and Muun URIs.
     */
    public static OperationUri fromString(String text) throws IllegalArgumentException {
        text = text.trim();
        
        try {
            return fromAddress(text);
        } catch (IllegalArgumentException ex) {
            // Not an address.
        }

        try {
            return fromBitcoinUri(text);
        } catch (IllegalArgumentException ex) {
            // Not a Bitcoin URI.
        }

        try {
            return fromMuunUri(text);
        } catch (IllegalArgumentException ex) {
            // Not a Muun URI.
        }

        // Sorry mate, we tried.
        throw new IllegalArgumentException(text);
    }

    /**
     * Create an OperationUri from a plain Bitcoin address. The address must belong to the network
     * in use, as specified by BuildConfig.
     */
    public static OperationUri fromAddress(String address) {
        if (!ValidationHelpers.isValidAddress(netParams, address)) {
            throw new IllegalArgumentException(address);
        }

        return new OperationUri(BITCOIN_SCHEME + ":" + address);
    }

    /**
     * Create an OperationUri from a "bitcoin:" URI.
     */
    public static OperationUri fromBitcoinUri(String bitcoinUri) {
        try {
            new BitcoinURI(netParams, bitcoinUri);

        } catch (BitcoinURIParseException e) {
            throw new IllegalArgumentException(bitcoinUri);
        }

        return new OperationUri(bitcoinUri);
    }

    /**
     * Create an OperationUri from a "muun:" URI.
     */
    public static OperationUri fromMuunUri(String muunUri) {
        if (!muunUri.startsWith(MUUN_SCHEME + ":")) {
            throw new IllegalArgumentException(muunUri);
        }

        return new OperationUri(muunUri);
    }

    /**
     * Create an OperationUri from a Houston user ID.
     */
    public static OperationUri fromContactHid(long contactHid) {
        final String content = new UriBuilder()
                .setScheme(MUUN_SCHEME)
                .setHost(MUUN_HOST_CONTACT)
                .setPath("" + contactHid)
                .build();

        return new OperationUri(content);
    }

    /**
     * Create a Muun OperationUri from an PaymentRequest.
     */
    public static OperationUri fromPaymentRequest(PaymentRequest draft) {
        final UriBuilder builder = new UriBuilder()
                .setScheme(MUUN_SCHEME)
                .addParam(MUUN_AMOUNT, draft.amount.getNumber().toString())
                .addParam(MUUN_CURRENCY, draft.amount.getCurrency().getCurrencyCode())
                .addParam(MUUN_DESCRIPTION, draft.description);

        if (draft.contact != null) {
            builder
                    .setHost(MUUN_HOST_CONTACT)
                    .setPath(draft.contact.hid.toString());

        } else if (draft.address != null) {
            builder
                    .setHost(MUUN_HOST_EXTERNAL)
                    .setPath(draft.address);
        }

        return new OperationUri(builder.build());
    }


    private final String original;
    private final UriParser parser;


    private OperationUri(String original) {
        this.original = original;
        this.parser = new UriParser(original);
    }

    public String getScheme() {
        return parser.getScheme();
    }

    public String getHost() {
        return parser.getHost();
    }

    public String getPath() {
        return parser.getPath();
    }

    public Optional<String> getParam(String name) {
        return Optional.ofNullable(parser.getParam(name));
    }

    public String toString() {
        return original;
    }

    public boolean isAsync() {
        return getAsyncUrl().isPresent();
    }

    public Optional<String> getAsyncUrl() {
        return Optional.ofNullable(parser.getParam(BIP72_PAYREQ_PARAM));
    }

    /**
     * If this URL is a BIP72 async payment request, return the host. Empty otherwise.
     */
    public Optional<String> getAsyncUrlHost() {
        try {
            return getAsyncUrl().map(url -> Uri.parse(url).getHost());
        } catch (Exception anyError) {
            return Optional.empty();
        }
    }

    public boolean isExternal() {
        return getScheme().equals(BITCOIN_SCHEME);
    }

    /**
     * Get the address contained in this URI, or empty if not included.
     */
    public Optional<String> getEmbeddedAddress() {
        if (isExternal() && !getHost().isEmpty()) {
            return Optional.of(getHost());
        } else {
            return Optional.empty();
        }
    }
}
