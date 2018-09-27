package io.muun.apollo.domain.utils;

import java.util.Objects;

public class NameValuePair {
    public final String name;
    public final String value;

    public NameValuePair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final NameValuePair that = (NameValuePair) obj;
        return Objects.equals(name, that.name) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return "(" + name + ": " + value + ")";
    }
}
