package io.muun.common.model;

import io.muun.common.Optional;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PhoneNumberTest {

    @Test
    public void testName() throws Exception {

        assertThat(getAreaCode(new PhoneNumber("111512345678", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("0111512345678", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("1112345678", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("01112345678", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("91112345678", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("+541112345678"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("+5491112345678"))).isEqualTo("11");

        assertThat(getAreaCode(new PhoneNumber("223151234567", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("0223151234567", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("2231234567", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("02231234567", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("92231234567", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("+542231234567"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("+5492231234567"))).isEqualTo("223");
    }

    private String getAreaCode(PhoneNumber phoneNumber) {

        final Optional<String> areaCode = phoneNumber.getAreaNumber();

        if (!areaCode.isPresent()) {
            throw new IllegalArgumentException();
        }

        return areaCode.get();
    }
}