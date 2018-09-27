package io.muun.apollo.data.serialization.dates;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class MuunZonedDateTimeDeserializer extends JsonDeserializer<MuunZonedDateTime> {

    @Override
    public MuunZonedDateTime deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {

        final ObjectCodec codec = parser.getCodec();
        final String serialized = codec.readValue(parser, String.class);
        return ApolloZonedDateTime.fromString(serialized);
    }
}
