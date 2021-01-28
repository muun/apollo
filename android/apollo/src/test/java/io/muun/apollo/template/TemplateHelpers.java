package io.muun.apollo.template;

import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Schema;
import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.model.Currency;
import io.muun.common.utils.Encodings;

import br.com.six2six.fixturefactory.base.Range;
import br.com.six2six.fixturefactory.function.AtomicFunction;
import br.com.six2six.fixturefactory.function.impl.RandomFunction;
import br.com.six2six.fixturefactory.function.impl.RegexFunction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitcoinj.params.TestNet3Params;
import org.javamoney.moneta.Money;
import rx.functions.Func0;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.UnknownCurrencyException;

public class TemplateHelpers {

    private static final RegexFunction PHONE_NUMBER = new RegexFunction("[+]54911[56]\\d{7}");

    private static final RegexFunction EMAIL = new RegexFunction("[^@]+@[^\\.]+\\..+");

    private static final RandomFunction HID = new RandomFunction(Long.class, new Range(0, 100000));

    private static final RandomFunction CURRENCY = new RandomFunction(getCurrencyUnits().toArray());

    private static final RegexFunction PROFILE_PICTURE_URL =
            new RegexFunction("https?://(www\\.)?\\w{5,10}\\.com/(\\w{5,10}/)?\\w{5,10}\\.png");

    private static final RandomFunction SATOSHIS =
            new RandomFunction(Long.class, new Range(1, 100000000));

    private static final RandomFunction AMOUNT =
            new RandomFunction(Double.class, new Range(0.0, 10000.0));

    private static final RegexFunction HASH256 = new RegexFunction("[0-9a-f]{64}");

    private static RandomFunction HOST = random("hostname.com", "fakeittill.youmakeit");

    private static RandomFunction PORT = new RandomFunction(Integer.class, new Range(0, 65535));

    public static AtomicFunction phoneNumber() {
        return PHONE_NUMBER;
    }

    public static AtomicFunction email() {
        return EMAIL;
    }

    public static AtomicFunction houstonId() {
        return HID;
    }

    /**
     * Workaround because our Fixture library doesn't support RandomFunctions of builtin types.
     */
    public static AtomicFunction houstonIdBuiltInLong() {
        return lambda(() -> getRandomLong(new Range(0, 100000)));
    }

    public static AtomicFunction uuid() {
        return lambda(TemplateHelpers::getUuid);
    }

    public static AtomicFunction currency() {
        return CURRENCY;
    }

    public static AtomicFunction profilePictureUrl() {
        return PROFILE_PICTURE_URL;
    }

    public static AtomicFunction satoshis() {
        return SATOSHIS;
    }

    public static AtomicFunction money(CurrencyUnit currency) {
        return lambda(() -> Money.of((Double) AMOUNT.generateValue(), currency));
    }

    public static AtomicFunction money() {
        return money(currency().generateValue());
    }

    public static AtomicFunction hash256() {
        return HASH256;
    }

    public static AtomicFunction port() {
        return PORT;
    }

    public static AtomicFunction host() {
        return HOST;
    }

    public static AtomicFunction serializedNetworkAddresses() {
        return lambda(TemplateHelpers::getSerializedNetworkAddresses);
    }

    public static RandomFunction random(Object... dataset) {
        return new RandomFunction(dataset);
    }

    public static AtomicFunction privateKey() {
        return lambda(() -> PrivateKey.getNewRootPrivateKey(TestNet3Params.get()));
    }

    public static AtomicFunction publicKey() {
        return lambda(TemplateHelpers::getPublicKey);
    }

    public static AtomicFunction publicKey(String derivationPath) {
        return lambda(() -> getPublicKey(derivationPath));
    }

    public static AtomicFunction publicKeyTriple(String derivationPath) {
        return lambda(() -> new PublicKeyTriple(
                getPublicKey(derivationPath),
                getPublicKey(derivationPath),
                getPublicKey(derivationPath)
        ));
    }

    private static PublicKey getPublicKey() {
        return PrivateKey.getNewRootPrivateKey(TestNet3Params.get()).getPublicKey();
    }

    private static PublicKey getPublicKey(String derivationPath) {

        try {
            return PrivateKey.getNewRootPrivateKey(TestNet3Params.get())
                    .deriveFromAbsolutePath(derivationPath)
                    .getPublicKey();
        } catch (KeyDerivationException e) {
            return getPublicKey(derivationPath);
        }
    }

    public static AtomicFunction contactPublicKey() {
        return publicKey(Schema.getContactsKeyPath());
    }

    public static AtomicFunction externalPublicKey() {
        return publicKey(Schema.getExternalKeyPath());
    }

    public static AtomicFunction externalPublicKeyTriple() {
        return publicKeyTriple(Schema.getExternalKeyPath());
    }

    public static AtomicFunction changePublicKey() {
        return publicKey(Schema.getChangeKeyPath());
    }

    public static AtomicFunction publicKeyBase58() {
        return lambda(() -> getPublicKey()
                .serializeBase58()
        );
    }

    public static AtomicFunction publicKeyHex() {
        return lambda(() -> Encodings.bytesToHex(getPublicKey().getPublicKeyBytes()));
    }

    public static AtomicFunction address() {
        return lambda(TemplateHelpers::getAddress);
    }

    public static AtomicFunction muunAddress() {
        return lambda(TemplateHelpers::getMuunAddress);
    }

    public static AtomicFunction lnInvoice() {
        return lambda(TemplateHelpers::getLnInvoice);
    }

    private static List<CurrencyUnit> getCurrencyUnits() {

        final List<CurrencyUnit> currencies = new ArrayList<>();

        for (String code : Currency.CURRENCIES.keySet()) {
            try {
                currencies.add(Monetary.getCurrency(code));
            } catch (UnknownCurrencyException ignored) {
                // ignored
            }
        }

        return currencies;
    }

    private static String getAddress() {
        return getPublicKey().toAddress();
    }

    private static MuunAddress getMuunAddress() {
        final PublicKey pubKey = getPublicKey();
        return new MuunAddress(pubKey.getAbsoluteDerivationPath(), pubKey.toAddress());
    }

    private static String getLnInvoice() {
        return "lnbc2500u1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdq5xysxxats"
                + "yp3k7enxv4jsxqzpuaztrnwngzn3kdzw5hydlzf03qdgm2hdq27cqv3agm2awhz5se903vruatfhq77w"
                + "3ls4evs3ch9zw97j25emudupq63nyw24cg27h2rspfj9srp";
    }

    private static String getSerializedNetworkAddresses() {
        final String[] networkAddresses = new String[]{
                getNetworkAddress(),
                getNetworkAddress(),
                getNetworkAddress()
        };

        try {
            return new ObjectMapper().writeValueAsString(Arrays.asList(networkAddresses));
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String getNetworkAddress() {
        return HOST.generateValue() + ":" + PORT.generateValue();
    }

    private static String getUuid() {
        return UUID.randomUUID().toString();
    }

    private static <T> AtomicFunction lambda(Func0<T> generator) {

        return new AtomicFunction() {
            @SuppressWarnings("unchecked")
            @Override
            public T generateValue() {
                return generator.call();
            }
        };
    }

    /**
     * Copied from {@link RandomFunction} because its private and we need it for built-in
     * constructor params.
     */
    private static long getRandomLong(Range range) {
        return Math.round(getRandomDouble(range));
    }

    /**
     * Copied from {@link RandomFunction} because its private and we need it for built-in
     * constructor params.
     */
    private static double getRandomDouble(Range range) {
        return range.getStart().doubleValue()
                + (Math.random() * (range.getEnd().doubleValue() - range.getStart().doubleValue()));
    }
}
