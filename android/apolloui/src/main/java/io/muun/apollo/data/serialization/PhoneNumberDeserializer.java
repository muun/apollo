package io.muun.apollo.data.serialization;

import io.muun.common.model.PhoneNumber;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;


public class PhoneNumberDeserializer extends JsonDeserializer<PhoneNumber> {
    @Override
    public PhoneNumber deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {

        final ObjectCodec codec = parser.getCodec();
        final String serialized = codec.readValue(parser, String.class);

        return new PhoneNumber(serialized);
    }
}
