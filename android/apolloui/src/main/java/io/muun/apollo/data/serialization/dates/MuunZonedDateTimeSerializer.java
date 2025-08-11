package io.muun.apollo.data.serialization.dates;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class MuunZonedDateTimeSerializer extends JsonSerializer<MuunZonedDateTime> {

    @Override
    public void serialize(MuunZonedDateTime value, JsonGenerator gen,
                          SerializerProvider serializers) throws IOException {

        final ApolloZonedDateTime apolloZonedDateTime = (ApolloZonedDateTime) value;

        gen.writeString(apolloZonedDateTime.toString());
    }
}
