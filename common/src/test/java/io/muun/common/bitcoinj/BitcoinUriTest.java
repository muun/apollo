package io.muun.common.bitcoinj;

import org.bitcoinj.core.Coin;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BitcoinUriTest {

    @Test
    public void constructor() throws BitcoinURIParseException {
        new BitcoinUri(MainNetParams.get(),
                "bitcoin:1BiDqY6xZJUmijoAhyXXVzCM1B13CKxREJ");
        new BitcoinUri(MainNetParams.get(),
                "bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");
    }

    @Test
    public void getters() throws BitcoinURIParseException {
        BitcoinUri uri = new BitcoinUri(
                MainNetParams.get(),
                "bitcoin:1BiDqY6xZJUmijoAhyXXVzCM1B13CKxREJ?amount=0.2&label=foo"
        );
        assertThat(uri.getAddress()).isEqualTo("1BiDqY6xZJUmijoAhyXXVzCM1B13CKxREJ");
        assertThat(uri.getAmount().value).isEqualTo(20000000);
        assertThat(uri.getLabel()).isEqualTo("foo");

        uri = new BitcoinUri(MainNetParams.get(),
                "bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");
        assertThat(uri.getAddress()).isEqualTo("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");
    }

    @Test
    public void convertToBitcoinUri() {
        final String uri = BitcoinUri.convertToBitcoinUri(
                MainNetParams.get(), "1BiDqY6xZJUmijoAhyXXVzCM1B13CKxREJ",
                Coin.valueOf(20000000), "foo", null
        );
        assertThat(uri)
                .isEqualTo("bitcoin:1BiDqY6xZJUmijoAhyXXVzCM1B13CKxREJ?amount=0.2&label=foo");
    }
}
