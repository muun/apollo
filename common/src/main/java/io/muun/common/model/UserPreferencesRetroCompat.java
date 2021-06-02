package io.muun.common.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;

/**
 * How to add new preferences:
 * 1. Decide the type of the field and it's default value.
 * 2. Add the preference here
 *  * Add a nullable field with a default value
 *  * Add it to the constructor too, non-null
 *  * Add it in the merge method
 * 3. In falcon:
 *  * Add the field to UserPreferences as non-optional without a default, adding it to copy
 *  * In StoredUserPreferences add the field with a default value
 *  * In StoredUserPreferences map the field in the constructor and toModel method
 * 4. In apollo:
 *  * Add the field to UserPreferences as non-optional without a default
 *  * In StoredUserPreferences add the field with a default value
 *  * In StoredUserPreferences map the field in the constructor and toModel method
 */
@JsonSerialize(using = UserPreferencesRetroCompat.Serializer.class)
public class UserPreferencesRetroCompat extends UserPreferences {

    public static class Serializer extends JsonSerializer<UserPreferences> {
        @Override
        public void serialize(UserPreferences value,
                              JsonGenerator gen,
                              SerializerProvider serializers) throws IOException {

            gen.writeStartObject();
            gen.writeBooleanField("receiveStrictMode", value.receiveStrictMode);
            gen.writeBooleanField("seenNewHome", value.seenNewHome);
            gen.writeEndObject();
        }
    }

    /**
     * JSON constructor.
     */
    public UserPreferencesRetroCompat() {
    }

    /**
     * Apollo constructor.
     */
    public UserPreferencesRetroCompat(final boolean receiveStrictMode,
                                      final boolean seenNewHome) {
        this.receiveStrictMode = receiveStrictMode;
        this.seenNewHome = seenNewHome;
    }
}
