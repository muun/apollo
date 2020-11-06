package io.muun.common.utils;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.exception.ParsingException;
import io.muun.common.model.BtcAmount;
import io.muun.common.utils.internal.Bech32;

import com.google.common.primitives.Bytes;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to decode and encode (and store the component data) of BOLT-11 Lighting Network
 * Invoice Protocol. A simple, extendable, QR-code-ready protocol for requesting payments
 * over Lightning.
 * Very much inspired in Alex Bosworth's JS ref implementation.
 * See:
 * https://github.com/lightningnetwork/lightning-rfc/blob/master/11-payment-encoding.md
 * https://github.com/alexbosworth/ln-service/blob/master/bolt11/parse_payment_request.js
 */
public class LnInvoice {

    private static final String MAINNET_HEADER = "bc";
    private static final String TESTNET_HEADER = "tb";
    private static final String REGTEST_HEADER = "bcrt";

    // NOTE: bech32 words/groups are 8-bit bytes with 5-bit data in them (the most significant bits)
    private static final int SIGNATURE_WORD_LENGTH = 104; // 104 * 5 = 520 bits = 65 bytes (64 + 1)
    private static final int SIGNATURE_BYTE_LENGTH = 64;
    private static final int RECOVERY_FLAG_BYTE_LENGTH = 1;
    private static final List<Integer> RECOVERY_FLAGS = Arrays.asList(0, 1, 2, 3);
    private static final int MULTIPLIER_MARKER_LENGTH = 1;
    private static final int DECIMAL_BASE = 10;
    private static final String NO_DECIMALS = "000";
    private static final BigInteger BTC_IN_SATOSHIS = new BigInteger("100000000", DECIMAL_BASE);
    private static final BigInteger MAX_BTC = new BigInteger("21000000", DECIMAL_BASE);
    private static final BigInteger MAX_SATOSHIS = MAX_BTC.multiply(BTC_IN_SATOSHIS);
    private static final BigInteger TEN = new BigInteger("10", DECIMAL_BASE);
    private static final int DECIMAL_QTY = 3;
    private static final int TRIM_BYTE_LENGTH = 1;
    private static final int TIMESTAMP_WORD_LENGTH = 7;
    private static final int MILLIS_PER_SEC = 1000;
    private static final int TAG_LEN_WORD_LENGTH = 2;
    private static final int PAYMENT_HASH_BYTE_LENGTH = 32;
    private static final int DESCRIPTION_HASH_BYTE_LENGTH = 32;
    private static final int DEFAULT_EXPIRATION_SECONDS = 3600;
    private static final int P2PKH_ADDRESS_VERSION = 17;
    private static final int P2SH_ADDRESS_VERSION = 18;
    private static final int WITNESS_V0_ADDRESS_VERSION = 0;
    private static final Map<String, String> DIVISORS;

    static {
        final Map<String, String> aMap = new HashMap<>();
        aMap.put("m", "1000");
        aMap.put("u", "1000000");
        aMap.put("n", "1000000000");
        aMap.put("p", "1000000000000");
        DIVISORS = Collections.unmodifiableMap(aMap);
    }

    private static final Map<Integer, Tag> TAGGED_FIELDS;

    static {
        final Map<Integer, Tag> aMap = new HashMap<>();
        aMap.put(1, new Tag("p", "payment_hash", "sha256_hash"));
        aMap.put(3, new Tag("r", "routing", "route"));
        aMap.put(6, new Tag("x", "expiry", "number"));
        aMap.put(9, new Tag("f", "fallback_address", "chain_address"));
        aMap.put(13, new Tag("d", "description", "string"));
        aMap.put(19, new Tag("n", "destination_public_key", "public_key"));
        aMap.put(23, new Tag("h", "description_hash", "sha256_hash"));
        aMap.put(24, new Tag("c", "min_final_cltv_expiry", "number"));
        TAGGED_FIELDS = Collections.unmodifiableMap(aMap);
    }

    private static final Pattern HEADER_REGEXP = Pattern.compile("^ln(\\S+?)(\\d*)([a-zA-Z]?)$");
    private static final Pattern HEADER_REGEXP_WITHOUT_AMOUNT = Pattern.compile("^ln(\\S+)$");
    private static final Pattern AMOUNT_MULTIPLIER_REGEXP = Pattern.compile("^[munp]$");
    private static final Pattern IS_NUMERICAL_REGEXP = Pattern.compile("^\\d+$");

    public final String original;
    public final List<String> addresses;
    public final Long cltvDelta;
    public final String createdAt;
    public final String description;
    public final String descriptionHash;
    public final String destinationPubKey;
    public final String expiresAt;
    public final String id; // paymentHash hex-encoded
    public final boolean isExpired;
    public final Amount amount;
    //private ??? routes;

    private LnInvoice(String original,
                      List<String> addresses,
                      Long cltvDelta,
                      ZonedDateTime createdAt,
                      String description,
                      String descriptionHash,
                      String destinationPubKey,
                      ZonedDateTime expiresAt,
                      String id,
                      Amount amount) {
        this.original = original;
        this.addresses = addresses;
        this.cltvDelta = cltvDelta;
        this.createdAt = createdAt.format(Dates.LN_DATE_TIME_FORMATTER);
        this.description = description;
        this.descriptionHash = descriptionHash;
        this.destinationPubKey = destinationPubKey;
        this.expiresAt = expiresAt.format(Dates.LN_DATE_TIME_FORMATTER);
        this.id = id;
        this.isExpired = expiresAt.compareTo(ZonedDateTime.now(ZoneOffset.UTC)) < 0;
        this.amount = amount;
    }

    public ZonedDateTime getExpirationTime() {
        return ZonedDateTime.parse(expiresAt, Dates.LN_DATE_TIME_FORMATTER);
    }

    /**
     * Decodes a Bech32 encoded LN Invoice into its component data.
     *
     * @param params        The network parameters that determine which network the invoice is from
     * @param bech32Invoice a Bech32 LN Invoice
     * @return a LnInvoice object with the parsed data
     */
    public static LnInvoice decode(NetworkParameters params, String bech32Invoice) {

        final String expectedHeader = getHeader(params);

        // Separate the Human Readable Part from the data part by decoding bech32
        final Pair<String, byte[]> pair;

        try {
            pair = Bech32.decode(bech32Invoice, Long.MAX_VALUE);
        } catch (IllegalArgumentException e) {
            // TODO throw error
            throw e;
        }

        final String hrp = pair.fst;
        final byte[] data = pair.snd;

        // Separate the signature words (fixed length) from the rest of the data
        final byte[] signature = ByteArray.slice(data, -SIGNATURE_WORD_LENGTH);
        final byte[] dataWithoutSig = ByteArray.slice(data, 0, data.length - SIGNATURE_WORD_LENGTH);

        byte[] sigBytes = unpackBits(signature, true);

        // There is a recovery flag at the end of the signature buffer
        final int recoveryFlag = ByteArray.slice(sigBytes, -RECOVERY_FLAG_BYTE_LENGTH)[0];

        // Eliminate the recovery flag from the signature buffer
        sigBytes = ByteArray.slice(sigBytes, 0, sigBytes.length - RECOVERY_FLAG_BYTE_LENGTH);

        if (!(RECOVERY_FLAGS.contains(recoveryFlag) || sigBytes.length != SIGNATURE_BYTE_LENGTH)) {
            throw new IllegalArgumentException("InvalidOrMissingSignature");
        }

        // Without reverse lookups, can't say that the multipier at the end must
        // have a number before it, so instead we parse, and if the second group
        // doesn't have anything, there's a good chance the last letter of the
        // coin type got captured by the third group, so just re-regex without
        // the number.
        // NOTE: remember amount is optional

        Matcher matcher = HEADER_REGEXP.matcher(hrp);

        boolean hasAmount = false;
        if (matcher.matches()) {

            hasAmount = matcher.group(2) != null && !matcher.group(2).isEmpty();

            if (!hasAmount) {
                matcher = HEADER_REGEXP_WITHOUT_AMOUNT.matcher(hrp);
            }
        }

        if (!matcher.matches()) {
            throw new IllegalArgumentException("InvalidPaymentRequestPrefix");
        }

        final String currencyCode = matcher.group(1);

        if (!expectedHeader.equals(currencyCode)) {
            throw new IllegalArgumentException("UnknownCurrencyCodeInPaymentRequest");
        }

        final Amount invoiceAmount;

        if (hasAmount) {

            final String amount = matcher.group(2);
            final String multiplier = matcher.group(3);

            invoiceAmount = buildAmount(amount, multiplier);

        } else {
            invoiceAmount = null;
        }

        // Separate timestamp from rest of data part
        final byte[] timestampData = ByteArray.slice(data, 0, TIMESTAMP_WORD_LENGTH);

        // Timestamp - left padded 0 bits
        final long timestampMs = dataToLong(timestampData) * MILLIS_PER_SEC;

        final Instant instant = Instant.ofEpochMilli(timestampMs);
        final ZonedDateTime createdAt = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);

        // Cut off the timestamp words
        final byte[] tags = ByteArray.slice(
                dataWithoutSig,
                TIMESTAMP_WORD_LENGTH,
                dataWithoutSig.length
        );

        // Let's parse the tagged fields

        Long cltvDelta = 9L; // Default is 9, if not specified.
        String description = null;
        String descriptionHash = null;
        final List<String> addresses = new ArrayList<>();
        byte[] paymentHash = null;
        ZonedDateTime expiresAt = null;

        int cursor = 0;
        while (cursor < tags.length) {

            // TagCode is encoded in one byte (5 bits)
            final int tagCode = tags[cursor];
            cursor++;

            final int tagLength = (int) dataToLong(
                    ByteArray.slice(tags, cursor, cursor + TAG_LEN_WORD_LENGTH)
            );
            cursor += TAG_LEN_WORD_LENGTH;

            final byte[] tagData = ByteArray.slice(tags, cursor, cursor + tagLength);
            cursor += tagLength;

            final Tag tag = TAGGED_FIELDS.get(tagCode);
            final String tagName = tag != null ? tag.name : "";

            switch (tagName) {
                case "c": // CLTV expiry
                    cltvDelta = dataToLong(tagData);
                    break;

                case "d": // Description of Payment
                    try {
                        final byte[] descriptionEncoded = unpackBits(tagData, true);
                        description = new String(descriptionEncoded, Charset.forName("UTF-8"));

                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("InvalidDescriptionInPaymentRequest");
                    }
                    break;


                case "f": // On-chain fallback address
                    try {

                        final String address = dataToAddress(params, tagData);

                        addresses.add(address);
                    } catch (IllegalArgumentException e) {
                        throw new ParsingException("PaymentRequestFallbackAddress");
                    }
                    break;

                case "h": // Description of Payment Hash

                    final byte[] descriptionHashEncoded;
                    try {
                        descriptionHashEncoded = unpackBits(tagData, true);

                        descriptionHash = Encodings.bytesToHex(descriptionHashEncoded);

                    } catch (IllegalArgumentException e) {
                        throw new ParsingException("PaymentRequestDescriptionHash");
                    }

                    if (descriptionHashEncoded.length != DESCRIPTION_HASH_BYTE_LENGTH) {
                        throw new IllegalArgumentException("InvalidDescriptionHashByteLength");
                    }

                    break;

                case "p": // Payment Hash
                    if (paymentHash != null) {
                        break;
                    }

                    try {
                        paymentHash = unpackBits(tagData, true);

                    } catch (IllegalArgumentException e) {
                        throw new ParsingException("PaymentRequestPaymentHash");
                    }

                    if (paymentHash.length != PAYMENT_HASH_BYTE_LENGTH) {
                        throw new IllegalArgumentException("InvalidPaymentHashByteLength");
                    }
                    break;

                case "r": // Route Hop Hints

                    // TODO

                    break;

                case "x": // Expiration Seconds
                    try {
                        expiresAt = paymentRequestExpiration(tagData, createdAt);

                    } catch (IllegalArgumentException e) {
                        expiresAt = null;
                    }
                    break;

                default: // Ignore unparsed tags
                    break;
            }
        }

        if (expiresAt == null) {
            expiresAt = paymentRequestExpiration(null, createdAt);
        }

        if (paymentHash == null) {
            throw new IllegalArgumentException("ExpectedPaymentHash");
        }

        // Perform public key recovery from signature (computationally expensive)

        final byte[] dataWithoutSigEncoded = unpackBits(dataWithoutSig, false);

        final byte[] hashBytes = Hashes.sha256(
                ByteArray.concat(
                        hrp.getBytes(StandardCharsets.UTF_8),
                        dataWithoutSigEncoded
                )
        );

        final String destination = recover(sigBytes, recoveryFlag, hashBytes);

        // TODO process hopHints and route info

        return new LnInvoice(
                bech32Invoice,
                addresses,
                cltvDelta,
                createdAt,
                description,
                descriptionHash,
                destination,
                expiresAt,
                Encodings.bytesToHex(paymentHash),
                invoiceAmount
        );
    }

    /**
     * Encode an invoice intended for incoming swap test suite.
     */
    public static String encodeForTest(final NetworkParameters networkParameters,
                                       final PrivateKey identityKey,
                                       final byte[] paymentHash,
                                       final BtcAmount amount,
                                       final int finalCltvDelta,
                                       final String description,
                                       final byte[] publicNodeKey,
                                       final long shortChannelId,
                                       final int feeBaseMsat,
                                       final int feeProportionalMillionths,
                                       final short ctlvExpiryDelta) {

        final String header = "ln" + getHeader(networkParameters) + amount.toSats() * 10 + "n";

        byte[] packedBytes = {};
        packedBytes = ByteArray.concat(
                packedBytes,
                packInt((int) ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond())
        );

        packedBytes = addTaggedField(packedBytes, 'p', packBits(paymentHash));
        packedBytes = addTaggedField(
                packedBytes, 'd', packBits(description.getBytes(StandardCharsets.UTF_8))
        );
        packedBytes = addTaggedField(packedBytes, 'c', packInt(finalCltvDelta));
        packedBytes = addTaggedField(packedBytes, 'r', packBits(buildRouteHintBytes(
                publicNodeKey,
                shortChannelId,
                feeBaseMsat,
                feeProportionalMillionths,
                ctlvExpiryDelta
        )));

        packedBytes = appendSignature(identityKey, header, packedBytes);

        return Bech32.encode(header, packedBytes);
    }

    private static byte[] appendSignature(final PrivateKey identityKey,
                                          final String header,
                                          byte[] packedBytes) {
        // Sign the data
        final Sha256Hash hashToSign;
        hashToSign = Sha256Hash.of(
                ByteArray.concat(header.getBytes(StandardCharsets.UTF_8), packedBytes)
        );

        final ECKey key = identityKey.getDeterministicKey().decompress();
        final ECKey.ECDSASignature sig = key.sign(hashToSign);
        final byte recoveryId = key.findRecoveryId(hashToSign, sig);

        final byte[] signatureBytes = new byte[32 + 32 + 1];
        System.arraycopy(Encodings.bigIntegerToBytes(sig.r, 32), 0, signatureBytes, 0, 32);
        System.arraycopy(Encodings.bigIntegerToBytes(sig.s, 32), 0, signatureBytes, 32, 32);
        signatureBytes[signatureBytes.length - 1] = recoveryId;

        return Bytes.concat(packedBytes, packBits(signatureBytes));
    }

    private static byte[] buildRouteHintBytes(final byte[] publicNodeKey,
                                              final long shortChannelId,
                                              final int feeBaseMsat,
                                              final int feeProportionalMillionths,
                                              final short finalCltvDelta) {

        final byte[] result = new byte[publicNodeKey.length + 8 + 4 + 4 + 2];
        System.arraycopy(publicNodeKey, 0, result, 0, 33);
        System.arraycopy(longToBytes(shortChannelId), 0, result, 33, 8);
        System.arraycopy(intToBytes(feeBaseMsat), 0, result, 41, 4);
        System.arraycopy(intToBytes(feeProportionalMillionths), 0, result, 44, 4);
        System.arraycopy(shortToBytes(finalCltvDelta), 0, result, 48, 2);
        return result;
    }

    private static byte[] addTaggedField(final byte[] existing,
                                         final char type,
                                         final byte[] packedBytes) {

        byte key = -1;
        for (final Map.Entry<Integer, Tag> tagEntry : TAGGED_FIELDS.entrySet()) {

            if (tagEntry.getValue().name.charAt(0) == type) {
                key = tagEntry.getKey().byteValue();
            }
        }

        if (key == -1) {
            throw new IllegalArgumentException();
        }

        return ByteArray.concat(
                ByteArray.concat(existing, new byte[] {key}),
                ByteArray.concat(
                        packLength(packedBytes.length),
                        packedBytes
                )
        );
    }

    private static String recover(byte[] signature, int recoveryFlag, byte[] hashBytes) {

        final BigInteger r = new BigInteger(1, ByteArray.slice(signature, 0, 32));
        final BigInteger s = new BigInteger(1, ByteArray.slice(signature, 32, 64));

        if (r.compareTo(ECKey.CURVE.getN()) >= 0 || s.compareTo(ECKey.CURVE.getN()) >= 0) {
            throw new IllegalArgumentException("Couldn't parse signature");
        }

        if (r.compareTo(BigInteger.ZERO) == 0  || s.compareTo(BigInteger.ZERO) == 0) {
            throw new IllegalArgumentException();
        }

        final ECKey.ECDSASignature ecdsaSig = new ECKey.ECDSASignature(r, s);
        final Sha256Hash hash = Sha256Hash.wrap(hashBytes);

        return ECKey.recoverFromSignature(recoveryFlag, ecdsaSig, hash, true).getPublicKeyAsHex();
    }

    private static ZonedDateTime paymentRequestExpiration(byte[] data, ZonedDateTime createdAt) {

        final long secondsToAdd = data != null ? dataToLong(data) : DEFAULT_EXPIRATION_SECONDS;
        return createdAt.plusSeconds(secondsToAdd);
    }

    private static String dataToAddress(NetworkParameters params, byte[] data) {


        final byte version = data[0];
        final byte[] hashData = ByteArray.slice(data, 1);


        final byte[] encodedAddress = unpackBits(hashData, true);

        switch (version) {

            case P2PKH_ADDRESS_VERSION:
                return LegacyAddress.fromPubKeyHash(params, encodedAddress).toString();

            case P2SH_ADDRESS_VERSION:
                return LegacyAddress.fromScriptHash(params, encodedAddress).toString();

            case WITNESS_V0_ADDRESS_VERSION:
                return Bech32SegwitAddress.encode(params, version, encodedAddress);

            default:
                return null;
        }
    }

    /**
     * Perform a conversion from a bech32 encoded array of bytes (each bytes containg only 5 bits
     * of data, the most significant 5 bits) to a traditional array of bytes.
     *
     * @param signature bech32 encoded array of bytes
     * @param trim whether or not to trim the last part of the resulting array
     * @return the decoded array of bytes
     */
    private static byte[] unpackBits(byte[] signature, boolean trim) {
        return convertBits(
                Bytes.asList(signature),
                5,
                8,
                true,
                trim
        );
    }

    /**
     * Convert from a regular array of bytes to a bech32 5-bit word array.
     */
    private static byte[] packBits(byte[] bytes) {
        return convertBits(Bytes.asList(bytes), 8, 5, true, false);
    }

    /**
     * Perform a conversion of the bytes in data:
     * - first, translate the values in each byte to fromBits, most significant bit first
     * - then, re-arrange those bits into groups of toBits bits.
     *
     * <p>For more information see decoding section of Bech32:
     * https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
     *
     * <p>TODO find out why is trim needed, why we can't just use same convertBits in SegwitAddress
     * We have 2 impls (Pieter Wuille's and Alex Bosworth's) that do different things. Each one
     * works fine but only for its own use case.
     */
    private static byte[] convertBits(List<Byte> data,
                                      int fromBits,
                                      int toBits,
                                      boolean pad,
                                      boolean trim) {

        int acc = 0;
        int bits = 0;
        final int maxv = (1 << toBits) - 1;
        final List<Byte> ret = new ArrayList<>();

        for (Byte value : data) {
            final short b = (short) (value & 0xff);

            if (b < 0) {
                throw new IllegalArgumentException();

            } else if ((b >> fromBits) > 0) {
                throw new IllegalArgumentException();
            }

            acc = (acc << fromBits) | b;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                ret.add((byte) ((acc >> bits) & maxv));
            }
        }

        if (pad && (bits > 0)) {
            ret.add((byte) ((acc << (toBits - bits)) & maxv));
        }

        if (trim && data.size() * fromBits % toBits != 0) {
            return ByteArray.slice(Bytes.toArray(ret), 0, ret.size() - TRIM_BYTE_LENGTH);

        } else {
            return Bytes.toArray(ret);
        }
    }

    private static byte[] packInt(final int value) {
        final int maxValue = (1 << 5) - 1;
        return new byte[] {
                (byte) ((value >> 30) & maxValue),
                (byte) ((value >> 25) & maxValue),
                (byte) ((value >> 20) & maxValue),
                (byte) ((value >> 15) & maxValue),
                (byte) ((value >> 10) & maxValue),
                (byte) ((value >> 5) & maxValue),
                (byte) (value & maxValue)
        };
    }

    private static byte[] packLength(final int value) {
        final int maxValue = (1 << 5) - 1;
        return new byte[] {
                (byte) ((value >> 5) & maxValue),
                (byte) (value & maxValue)
        };
    }

    /**
     * Convert bech32 encoded array of bytes to big endian long.
     *
     * @param data bech32 encoded array of bytes
     * @return big endian long
     */
    private static long dataToLong(byte[] data) {
        long ret = 0;
        for (int i = data.length - 1, j = 0; i >= 0; i--, j++) {
            ret += (data[i] * Math.pow(32, j));
        }

        return ret;
    }

    private static byte[] shortToBytes(final short value) {
        return Encodings.bigIntegerToBytes(BigInteger.valueOf(value), 2);
    }

    private static byte[] intToBytes(final int value) {
        return Encodings.bigIntegerToBytes(BigInteger.valueOf(value), 4);
    }

    private static byte[] longToBytes(final long value) {
        return Encodings.bigIntegerToBytes(BigInteger.valueOf(value), 8);
    }

    private static Amount buildAmount(String amount, String multiplier) {

        final BigInteger amountInSatoshis;

        if (multiplier != null) {
            final Matcher matcher = AMOUNT_MULTIPLIER_REGEXP.matcher(multiplier);

            if (multiplier.length() > MULTIPLIER_MARKER_LENGTH || !matcher.matches()) {
                throw new IllegalArgumentException("InvalidAmountMultiplier");
            }
        }

        if (!IS_NUMERICAL_REGEXP.matcher(amount).matches()) {
            throw new IllegalArgumentException("InvalidAmount");
        }

        final BigInteger value = new BigInteger(amount, DECIMAL_BASE);
        final String decimals;

        if (multiplier != null && !multiplier.isEmpty()) {

            final BigInteger div = new BigInteger(DIVISORS.get(multiplier), DECIMAL_BASE);

            final BigInteger[] quotientRemainder = value.multiply(BTC_IN_SATOSHIS)
                    .divideAndRemainder(div);
            final BigInteger quotient = quotientRemainder[0];
            final BigInteger remainder = quotientRemainder[1];

            final boolean hasDecimals = !remainder.toString().equals("0");

            decimals = !hasDecimals ? NO_DECIMALS : extractDecimals(remainder, div, DECIMAL_QTY);
            amountInSatoshis = quotient;

        } else {
            decimals = NO_DECIMALS;
            amountInSatoshis = value.multiply(BTC_IN_SATOSHIS);
        }

        if (amountInSatoshis.compareTo(MAX_SATOSHIS) > 0) {
            throw new IllegalArgumentException("TokenCountExceedsMaximumValue");
        }

        return new Amount(amountInSatoshis.toString() + decimals, amountInSatoshis.longValue());
    }

    private static String extractDecimals(BigInteger remainder, BigInteger div, int decimalQty) {

        final StringBuilder decimals = new StringBuilder();
        for (int i = 0; i < decimalQty; i++) {

            final BigInteger[] quotientRemainder = remainder.multiply(TEN).divideAndRemainder(div);

            remainder = quotientRemainder[1];
            decimals.append(quotientRemainder[0]);
        }

        return decimals.toString();
    }

    // TODO encode

    private static String getHeader(NetworkParameters networkParameters) {
        if (NetworkParameters.ID_MAINNET.equals(networkParameters.getId())) {
            return MAINNET_HEADER;

        } else if (NetworkParameters.ID_REGTEST.equals(networkParameters.getId())) {
            return REGTEST_HEADER;

        } else {
            return TESTNET_HEADER;
        }
    }

    public static class Amount {

        public final String amountWithMillis;
        public final long amountInSatoshis;

        private Amount(String amountWithMillis, long amountInSatoshis) {
            this.amountWithMillis = amountWithMillis;
            this.amountInSatoshis = amountInSatoshis;
        }
    }

    private static class Tag {

        final String name;
        final String label;
        final String type;

        private Tag(String name, String label, String type) {
            this.label = label;
            this.name = name;
            this.type = type;
        }
    }
}
