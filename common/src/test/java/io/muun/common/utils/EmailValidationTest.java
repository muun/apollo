package io.muun.common.utils;

import io.muun.common.bitcoinj.ValidationHelpers;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EmailValidationTest {

    @Test
    public void typicalOk() {
        assertValid("user@domain.com");
        assertValid("user@domain.com.ar");
        assertValid("user@domain.com.ar.what");
    }

    @Test
    public void someSymbolsOk() {
        assertValid("user-123@domain.com");
        assertValid("user_123@domain.com");
        assertValid("user+123@domain.com");
        assertValid("user.123@domain.com");
        assertValid("user@dom-123.com");
    }

    @Test
    public void nullFails() {
        assertInvalid(null);
    }

    @Test
    public void emptyFails() {
        assertInvalid("");
    }

    @Test
    public void whitespaceFails() {
        assertInvalid("     ");
        assertInvalid("user@domain  .com");
        assertInvalid("user@domain. com"); // actually happened to a user
    }

    @Test
    public void noDomainFails() {
        assertInvalid("user.com");
        assertInvalid("user@.com");
    }

    @Test
    public void multiAtFails() {
        assertInvalid("user@@domain.com"); // actually happened to a tester
        assertInvalid("us@er@domain.com");
    }

    private void assertValid(String email) {
        assertThat(ValidationHelpers.isValidEmail(email)).isTrue();
    }

    private void assertInvalid(String email) {
        assertThat(ValidationHelpers.isValidEmail(email)).isFalse();
    }
}
