package io.muun.apollo.domain.model;

import io.muun.apollo.BaseTest;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PhoneContactTest extends BaseTest {

    @Test
    public void computeHash() {

        assertThat(PhoneContact.computeHash("+5491112345678"))
                .isEqualTo("cc1b0625e1c9f4ccf2de78e47e324f3c4babd9b2bb73113c5e97c863549c5dbb");
    }
}