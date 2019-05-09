package io.muun.apollo.domain.model;

import io.muun.apollo.domain.utils.UriBuilder;
import io.muun.apollo.domain.utils.UriParser;
import io.muun.apollo.external.Globals;
import io.muun.common.Optional;
import io.muun.common.bitcoinj.BitcoinUri;
import io.muun.common.bitcoinj.ValidationHelpers;
import io.muun.common.utils.LnInvoice;

import android.net.Uri;
import android.support.annotation.VisibleForTesting;
import org.bitcoinj.uri.BitcoinURIParseException;

public class OperationUri {

    private static final String BIP72_PAYREQ_PARAM = "r";

    public static final String MUUN_SCHEME = "muun";
    public static final String BITCOIN_SCHEME = "bitcoin";
    public static final String LN_SCHEME = "lightning";

    public static final String MUUN_HOST_CONTACT = "contacts";
    public static final String MUUN_HOST_EXTERNAL = "external";
    public static final String MUUN_HOST_DEPOSIT = "deposit";
    public static final String MUUN_HOST_WITHDRAW = "withdraw";

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
            return fromLnInvoice(text);
        } catch (IllegalArgumentException ex) {
            // Not a Lighning Network raw invoice.
        }

        try {
            return fromLnUri(text);
        } catch (IllegalArgumentException ex) {
            // Not a Lighning Network URI.
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
     * in use, as specified by Globals.INSTANCE.
     */
    @VisibleForTesting
    public static OperationUri fromAddress(String address) {
        if (!ValidationHelpers.isValidAddress(Globals.INSTANCE.getNetwork(), address)) {
            throw new IllegalArgumentException(address);
        }

        return new OperationUri(BITCOIN_SCHEME + ":" + address);
    }

    /**
     * Create an OperationUri from a "bitcoin:" URI.
     */
    @VisibleForTesting
    public static OperationUri fromBitcoinUri(String bitcoinUri) {
        try {
            new BitcoinUri(Globals.INSTANCE.getNetwork(), bitcoinUri);

        } catch (BitcoinURIParseException e) {
            throw new IllegalArgumentException(bitcoinUri);
        }

        return new OperationUri(bitcoinUri);
    }

    /**
     * Create an OperationUri from a "lightning:" URI.
     */
    @VisibleForTesting
    public static OperationUri fromLnUri(String lnUri) {
        if (!lnUri.toLowerCase().startsWith(LN_SCHEME + ":")) {
            throw new IllegalArgumentException(lnUri);
        }

        return fromLnInvoice(lnUri.substring(LN_SCHEME.length() + 1));
    }

    /**
     * Create an OperationUri from a raw LN invoice.
     */
    @VisibleForTesting
    public static OperationUri fromLnInvoice(String lnInvoice) {
        try {
            LnInvoice.decode(Globals.INSTANCE.getNetwork(), lnInvoice);
        } catch (Exception ex) {
            throw new IllegalArgumentException(lnInvoice, ex);
        }

        return new OperationUri(LN_SCHEME + ":" + lnInvoice);
    }

    /**
     * Create an OperationUri from a "muun:" URI.
     */
    @VisibleForTesting
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
        return fromMuunEntityId(MUUN_HOST_CONTACT, contactHid);
    }

    /**
     * Create an OperationUri from a HardwareWallet ID, to make a deposit.
     */
    public static OperationUri depositFromHardwareWalletHid(long hardwareWalletHid) {
        return fromMuunEntityId(MUUN_HOST_DEPOSIT, hardwareWalletHid);
    }

    /**
     * Create an OperationUri from a HardwareWallet ID, to make a deposit.
     */
    public static OperationUri withdrawFromHardwareWalletHid(long hardwareWalletHid) {
        return fromMuunEntityId(MUUN_HOST_WITHDRAW, hardwareWalletHid);
    }

    private static OperationUri fromMuunEntityId(String host, long id) {
        final String content = new UriBuilder()
                .setScheme(MUUN_SCHEME)
                .setHost(host)
                .setPath("" + id)
                .build();

        return new OperationUri(content);
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
        return getAsyncUrl().isPresent() || isLn();
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

    public boolean isDeposit() {
        return getHost().equals(MUUN_HOST_DEPOSIT);
    }

    public boolean isWithdrawal() {
        return getHost().equals(MUUN_HOST_WITHDRAW);
    }

    public boolean isLn() {
        return getScheme().equals(LN_SCHEME);
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
