package io.muun.common.model;

import io.muun.common.Optional;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PhoneNumberTest {

    @Test
    public void testName() {

        assertThat(getAreaCode(new PhoneNumber("111565066097", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("0111565066097", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("1165066097", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("01165066097", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("91165066097", "AR"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("+541165066097"))).isEqualTo("11");
        assertThat(getAreaCode(new PhoneNumber("+5491165066097"))).isEqualTo("11");

        assertThat(getAreaCode(new PhoneNumber("223155636868", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("0223155636868", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("2235636868", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("02235636868", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("92235636868", "AR"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("+542235636868"))).isEqualTo("223");
        assertThat(getAreaCode(new PhoneNumber("+5492235636868"))).isEqualTo("223");
    }

    @Test
    public void testBrazilianNumber() {
        assertThat(getAreaCode(new PhoneNumber("21980010499", "BR"))).isEqualTo("21");
        assertThat(getAreaCode(new PhoneNumber("+5521980010499"))).isEqualTo("21");
    }

    private String getAreaCode(PhoneNumber phoneNumber) {

        final Optional<String> areaCode = phoneNumber.getAreaNumber();

        if (!areaCode.isPresent()) {
            throw new IllegalArgumentException();
        }

        return areaCode.get();
    }
}