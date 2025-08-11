package io.muun.apollo.data.serialization;

import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.common.api.BitcoinAmountJson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;


public class BitcoinAmountDeserializer extends JsonDeserializer<BitcoinAmount> {

    @Override
    public BitcoinAmount deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {

        final ObjectCodec codec = parser.getCodec();
        final BitcoinAmountJson json = codec.readValue(parser, BitcoinAmountJson.class);

        return new BitcoinAmount(
                json.inSatoshis,
                json.inInputCurrency,
                json.inPrimaryCurrency
        );
    }
}
