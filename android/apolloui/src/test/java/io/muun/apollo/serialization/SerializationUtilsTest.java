package io.muun.apollo.serialization;

import io.muun.apollo.data.serialization.SerializationUtils;

import org.junit.Test;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class SerializationUtilsTest {


    @Test
    public void genericSerialization() {
        final SomeObject before = new SomeObject(1234, "hello", false, null);

        // NOTE:
        // We'll test this using strings, because we want to isolate this case from the actual class
        // involved, without using any of the typed serialization methods.

        final String json = SerializationUtils.serializeJson(Object.class, before);
        assertThat(json).isEqualTo("{\"myInt\":1234,\"myString\":\"hello\",\"myBool\":false,"
                + "\"myRef\":null}");

        final Object after = SerializationUtils.deserializeJson(Object.class, json);
        assertThat(after.toString()).isEqualTo("{myInt=1234, myString=hello, myBool=false, "
                + "myRef=null}");
    }

    @Test
    public void genericSerializationDeep() {
        final SomeObject before = new SomeObject(
                1234,
                "hello",
                false,
                new SomeObject(4321, "goodbye", true, null)
        );

        // NOTE: same as above, using strings here.

        final String json = SerializationUtils.serializeJson(Object.class, before);
        assertThat(json).isEqualTo(
                "{\"myInt\":1234,\"myString\":\"hello\",\"myBool\":false,\"myRef\":{\"myInt\":4321,"
                        + "\"myString\":\"goodbye\",\"myBool\":true,\"myRef\":null}}"
        );

        final Object after = SerializationUtils.deserializeJson(Object.class, json);
        assertThat(after.toString()).isEqualTo(
                "{myInt=1234, myString=hello, myBool=false, myRef={myInt=4321, myString=goodbye, "
                        + "myBool=true, myRef=null}}"
        );
    }

    private static class SomeObject {
        public int myInt;
        public String myString;
        public Boolean myBool;
        public SomeObject myRef;

        public SomeObject(int myInt, String myString, Boolean myBool, SomeObject myRef) {
            this.myInt = myInt;
            this.myString = myString;
            this.myBool = myBool;
            this.myRef = myRef;
        }
    }
}
