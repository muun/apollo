package io.muun.apollo.data.serialization.dates;

import io.muun.apollo.data.serialization.SerializationUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.threeten.bp.ZonedDateTime;

import java.io.IOException;

public class ZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

    @Override
    public ZonedDateTime deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {

        final ObjectCodec codec = parser.getCodec();
        final String serialized = codec.readValue(parser, String.class);

        return SerializationUtils.deserializeDate(serialized);
    }
}
