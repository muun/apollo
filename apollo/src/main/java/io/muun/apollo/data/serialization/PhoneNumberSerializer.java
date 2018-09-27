package io.muun.apollo.data.serialization;

import io.muun.common.model.PhoneNumber;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class PhoneNumberSerializer extends JsonSerializer<PhoneNumber> {

    @Override
    public void serialize(PhoneNumber value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {

        gen.writeString(value.toE164String());
    }
}
