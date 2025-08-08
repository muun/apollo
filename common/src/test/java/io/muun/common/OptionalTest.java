package io.muun.common;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class OptionalTest {

    @Test
    public void streamPresent() {

        final Optional<String> optional = Optional.of("foo");

        final List<String> expected = new ArrayList<>();
        expected.add("foo");

        assertEquals(
                expected,
                optional.stream().collect(Collectors.toList())
        );
    }

    @Test
    public void streamEmpty() {

        final Optional<String> optional = Optional.empty();

        assertEquals(
                new ArrayList<>(),
                optional.stream().collect(Collectors.toList())
        );
    }

    @Test
    public void streamFlatMap() {

        final List<Optional<String>> optionals = new ArrayList<>();
        optionals.add(Optional.of("foo"));
        optionals.add(Optional.empty());

        final List<String> expected = new ArrayList<>();
        expected.add("foo");

        assertEquals(
                expected,
                optionals.stream().flatMap(Optional::stream).collect(Collectors.toList())
        );
    }

}
