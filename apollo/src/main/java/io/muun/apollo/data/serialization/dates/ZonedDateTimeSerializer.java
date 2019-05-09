package io.muun.apollo.data.serialization.dates;

import io.muun.apollo.data.serialization.SerializationUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.threeten.bp.ZonedDateTime;

import java.io.IOException;

public class ZonedDateTimeSerializer extends JsonSerializer<ZonedDateTime> {

    @Override
    public void serialize(ZonedDateTime value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {

        gen.writeString(SerializationUtils.serializeDate(value));
    }
}
