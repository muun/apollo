package io.muun.apollo.template;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Schema;
import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.model.Currency;

import br.com.six2six.fixturefactory.base.Range;
import br.com.six2six.fixturefactory.function.AtomicFunction;
import br.com.six2six.fixturefactory.function.impl.RandomFunction;
import br.com.six2six.fixturefactory.function.impl.RegexFunction;
import org.bitcoinj.params.TestNet3Params;
import org.javamoney.moneta.Money;
import rx.functions.Func0;

import java.util.ArrayList;
import java.util.List;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.UnknownCurrencyException;

public class TemplateHelpers {

    private static final RegexFunction PHONE_NUMBER = new RegexFunction("[+]54911[56]\\d{7}");

    private static final RandomFunction HID = new RandomFunction(Long.class, new Range(0, 100000));

    private static final RandomFunction CURRENCY = new RandomFunction(getCurrencyUnits().toArray());

    private static final RegexFunction PROFILE_PICTURE_URL =
            new RegexFunction("https?://(www\\.)?\\w{5,10}\\.com/(\\w{5,10}/)?\\w{5,10}\\.png");

    private static final RandomFunction SATOSHIS =
            new RandomFunction(Long.class, new Range(1, 100000000));

    private static final RandomFunction AMOUNT =
            new RandomFunction(Double.class, new Range(0.0, 10000.0));

    private static final RegexFunction HASH256 = new RegexFunction("[0-9a-f]{64}");

    public static AtomicFunction phoneNumber() {
        return PHONE_NUMBER;
    }

    public static AtomicFunction houstonId() {
        return HID;
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

    public static AtomicFunction publicKey(String derivationPath) {
        return lambda(() -> getPublicKey(derivationPath));
    }

    public static AtomicFunction contactPublicKey() {
        return publicKey(Schema.getContactsKeyPath());
    }

    public static AtomicFunction externalPublicKey() {
        return publicKey(Schema.getExternalKeyPath());
    }

    public static AtomicFunction changePublicKey() {
        return publicKey(Schema.getChangeKeyPath());
    }

    public static AtomicFunction privateKey() {
        return lambda(() -> PrivateKey.getNewRootPrivateKey(TestNet3Params.get()));
    }

    public static AtomicFunction address() {
        return lambda(TemplateHelpers::getAddress);
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

    private static PublicKey getPublicKey(String derivationPath) {

        try {
            return PrivateKey.getNewRootPrivateKey(TestNet3Params.get())
                    .deriveFromAbsolutePath(derivationPath)
                    .getPublicKey();
        } catch (KeyDerivationException e) {
            return getPublicKey(derivationPath);
        }
    }

    private static String getAddress() {

        return PrivateKey.getNewRootPrivateKey(TestNet3Params.get())
                .getPublicKey()
                .toAddress();
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
}
