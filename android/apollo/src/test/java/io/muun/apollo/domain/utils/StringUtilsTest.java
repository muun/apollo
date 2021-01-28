package io.muun.apollo.domain.utils;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringUtilsTest {

    @Test
    public void isEmpty() {
        assertThat(StringUtils.isEmpty(null)).isTrue();
        assertThat(StringUtils.isEmpty("")).isTrue();
        assertThat(StringUtils.isEmpty("  ")).isTrue();
    }

    @Test
    public void join() {
        assertThat(StringUtils.join(", ", new String[]{})).isEqualTo("");
        assertThat(StringUtils.join(", ", new String[]{"foo"})).isEqualTo("foo");
        assertThat(StringUtils.join(", ", new String[]{"foo", "bar"})).isEqualTo("foo, bar");
    }

    @Test
    public void joinText() {
        assertJoinText(", ", new String[]{}, "");
        assertJoinText(", ", new String[]{"foo", "bar"}, "foo, bar");
        assertJoinText(", ", new String[]{"foo", null, "", "bar"}, "foo, bar");
    }

    private void assertJoinText(String delimiter, String[] strings, String expected) {
        assertThat(StringUtils.joinText(delimiter, strings)).isEqualTo(expected);
    }
}
