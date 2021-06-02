package io.muun.apollo.domain.model;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.domain.libwallet.Invoice;
import io.muun.apollo.domain.libwallet.LnUrl;
import io.muun.apollo.domain.utils.UriBuilder;
import io.muun.apollo.domain.utils.UriParser;
import io.muun.common.Optional;
import io.muun.common.bitcoinj.BitcoinUri;
import io.muun.common.bitcoinj.ValidationHelpers;
import io.muun.common.utils.Preconditions;

import android.net.Uri;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.uri.BitcoinURIParseException;

import java.util.regex.Pattern;

public class OperationUri {

    private static final String BIP72_PAYREQ_PARAM = "r";
    private static final String BOLT11_INVOICE_PARAM = "lightning";

    public static final String MUUN_SCHEME = "muun";
    public static final String BITCOIN_SCHEME = "bitcoin";
    public static final String LN_SCHEME = "lightning";

    public static final String MUUN_HOST_CONTACT = "contacts";
    public static final String MUUN_HOST_EXTERNAL = "external";

    public static final String MUUN_AMOUNT = "amount";
    public static final String MUUN_CURRENCY = "currency";
    public static final String MUUN_DESCRIPTION = "message";

    /**
     * Create an OperationUri from any input String, by trying to use the other factory methods.
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
            return fromLnUrl(text);
        } catch (IllegalArgumentException ex) {
            // Not a LNURL.
        }

        try {
            return fromLnUrlUri(text);
        } catch (IllegalArgumentException ex) {
            // Not a LNURL URI.
        }

        try {
            return fromMuunBitcoinUri(text);
        } catch (IllegalArgumentException ex) {
            // Not a Muun Bitcoin URI.
        }

        try {
            return fromMuunLightningUri(text);
        } catch (IllegalArgumentException ex) {
            // Not a Muun Lightning URI.
        }

        try {
            return fromMuunLnUrlUri(text);
        } catch (IllegalArgumentException ex) {
            // Not a Muun LNURL URI.
        }

        //  This needs to go last as its the most permissive
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
    public static OperationUri fromAddress(String address) {
        if (!ValidationHelpers.isValidAddress(Globals.INSTANCE.getNetwork(), address)) {
            throw new IllegalArgumentException(address);
        }

        return new OperationUri(BITCOIN_SCHEME + ":" + address);
    }

    /**
     * Create an OperationUri from a "bitcoin:" URI.
     */
    public static OperationUri fromBitcoinUri(String bitcoinUri) {
        // workaround for some uris that include this invalid char (e.g satoshitango)
        bitcoinUri = bitcoinUri.replace("|", "%7C");

        final NetworkParameters network = Globals.INSTANCE.getNetwork();
        final String uriScheme = network.getUriScheme();

        // some sites put the uri in all caps and BitcoinJ doesn't like that
        if (bitcoinUri.toLowerCase().startsWith(uriScheme)) {
            bitcoinUri = bitcoinUri.replaceFirst(
                    Pattern.quote(bitcoinUri.substring(0, uriScheme.length())),
                    uriScheme
            );
        }

        try {
            new BitcoinUri(network, bitcoinUri);

        } catch (BitcoinURIParseException e) {
            throw new IllegalArgumentException(bitcoinUri);
        }

        return new OperationUri(bitcoinUri);
    }

    /**
     * Create an OperationUri from a "lightning:" URI.
     */
    public static OperationUri fromLnUri(String lnUri) {
        if (!lnUri.toLowerCase().startsWith(LN_SCHEME + ":")) {
            throw new IllegalArgumentException(lnUri);
        }

        return fromLnInvoice(lnUri.substring(LN_SCHEME.length() + 1));
    }

    /**
     * Create an OperationUri from a raw LN invoice.
     */
    public static OperationUri fromLnInvoice(String lnInvoice) {
        if (lnInvoice.toLowerCase().startsWith(LN_SCHEME + ":")) {
            // Should be handled by fromLnUri. Invoice#parseInvoice can parse it (if it its
            // lightning:<invoice>) but we can end up with lightning:lightning:<invoice>.
            throw new IllegalArgumentException(lnInvoice);
        }

        if (lnInvoice.toLowerCase().startsWith(MUUN_SCHEME + ":")) {
            // Should be handled by fromMuunLightningUri. Invoice#parseInvoice can parse it (if it
            // its muun:<invoice>) but we can end up with lightning:muun:<invoice>.
            throw new IllegalArgumentException(lnInvoice);
        }

        try {
            Invoice.INSTANCE.parseInvoice(Globals.INSTANCE.getNetwork(), lnInvoice);
        } catch (Exception ex) {
            throw new IllegalArgumentException(lnInvoice, ex);
        }

        return new OperationUri(LN_SCHEME + ":" + lnInvoice);
    }

    /**
     * Create an OperationUri from a "muun:{address}?amount={amount}" URI.
     */
    public static OperationUri fromMuunBitcoinUri(String muunBitcoinUri) {
        if (!muunBitcoinUri.startsWith(MUUN_SCHEME + ":")) {
            throw new IllegalArgumentException(muunBitcoinUri);
        }

        final String bitcoinScheme = Globals.INSTANCE.getNetwork().getUriScheme();
        return fromBitcoinUri(bitcoinScheme + muunBitcoinUri.substring(MUUN_SCHEME.length()));
    }

    /**
     * Create an OperationUri from a "muun:{invoice}" URI.
     */
    public static OperationUri fromMuunLightningUri(String muunLightningUri) {
        if (!muunLightningUri.startsWith(MUUN_SCHEME + ":")) {
            throw new IllegalArgumentException(muunLightningUri);
        }

        return fromLnUri(LN_SCHEME + muunLightningUri.substring(MUUN_SCHEME.length()));
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
     * Create an OperationUri from an LNURL.
     */
    public static OperationUri fromLnUrl(String lnUrl) {
        if (lnUrl.toLowerCase().startsWith(LN_SCHEME + ":")) {
            // Should be handled by fromLnUrlUri. LnUrl#isvalid can parse it (if it its
            // lightning:<LNURL>) but we can end up with lightning:lightning:<LNURL>.
            throw new IllegalArgumentException(lnUrl);
        }

        if (lnUrl.toLowerCase().startsWith(MUUN_SCHEME + ":")) {
            // Should be handled by fromMuunLnUrlUri. LnUrl#isvalid can parse it (if it its
            // muun:<LNURL>) but we can end up with lightning:muun:<LNURL>.
            throw new IllegalArgumentException(lnUrl);
        }

        if (!LnUrl.INSTANCE.isValid(lnUrl)) {
            throw new IllegalArgumentException(lnUrl);
        }

        return new OperationUri(LN_SCHEME + ":" + lnUrl);
    }

    /**
     * Create an OperationUri from a "lightning:{LNURL}" URI.
     */
    public static OperationUri fromLnUrlUri(String lnUrlUri) {
        if (!lnUrlUri.toLowerCase().startsWith(LN_SCHEME + ":")) {
            throw new IllegalArgumentException(lnUrlUri);
        }

        return fromLnUrl(lnUrlUri.substring(LN_SCHEME.length() + 1));
    }

    /**
     * Create an OperationUri from a "muun:{LNURL}" URI.
     */
    public static OperationUri fromMuunLnUrlUri(String muunLnUrlUri) {
        if (!muunLnUrlUri.toLowerCase().startsWith(MUUN_SCHEME + ":")) {
            throw new IllegalArgumentException(muunLnUrlUri);
        }

        return fromLnUrlUri(LN_SCHEME + muunLnUrlUri.substring(MUUN_SCHEME.length()));
    }

    /**
     * Create an OperationUri from a Houston user ID.
     */
    public static OperationUri fromContactHid(long contactHid) {
        return fromMuunEntityId(MUUN_HOST_CONTACT, contactHid);
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

    public boolean isMuun() {
        return getScheme().equals(MUUN_SCHEME);
    }

    public boolean isBitcoin() {
        return getScheme().equals(BITCOIN_SCHEME);
    }

    public boolean isLn() {
        return getScheme().equals(LN_SCHEME) && Invoice.INSTANCE.isValid(getHost());
    }

    public long getContactHid() {
        Preconditions.checkArgument(getHost().equals(MUUN_HOST_CONTACT));
        return Long.parseLong(getPath());
    }

    public String getExternalAddress() {
        Preconditions.checkArgument(getHost().equals(MUUN_HOST_EXTERNAL));
        return getPath();
    }

    /**
     * Get the address contained in this URI, or empty if not included.
     */
    public Optional<String> getBitcoinAddress() {
        if (isBitcoin()) {
            return Optional.ifNotEmpty(getHost());

        } else {
            return Optional.empty();
        }
    }

    /**
     * Get the Lightning invoice contained in this URI, or empty if not included.
     */
    public Optional<String> getLnInvoice() {
        if (isLn()) {
            return Optional.ifNotEmpty(getHost());

        } else if (isBitcoin()) {
            return getParam(BOLT11_INVOICE_PARAM);

        } else {
            return Optional.empty();
        }
    }

    /**
     * Get the LNURL contained in this URI, or empty if not included.
     */
    public Optional<String> getLnUrl() {
        if (LnUrl.INSTANCE.isValid(original)) {
            return Optional.ifNotEmpty(getHost());

        } else {
            return Optional.empty();
        }
    }
}
