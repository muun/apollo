package io.muun.common.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserPreferencesTest {

    @Test
    public void testUserPreferencesRetroCompat() throws JsonProcessingException {
        final UserPreferencesRetroCompat preferences = new UserPreferencesRetroCompat(
                true, true
        );

        final String result = new ObjectMapper().writeValueAsString(preferences);
        assertThat(result).isEqualTo("{\"receiveStrictMode\":true,\"seenNewHome\":true}");
    }

}
