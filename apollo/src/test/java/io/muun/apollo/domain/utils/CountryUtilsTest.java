package io.muun.apollo.domain.utils;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CountryUtilsTest {
    @Test
    public void hasMultipleCountries() throws Exception {
        assertThat(CountryUtils.hasMultipleCountries(1)).isTrue();
    }

    @Test
    public void mostPopularCountryCodeForPrefix() throws Exception {
        assertThat(CountryUtils.mostPopularCountryCodeForPrefix(1)).isEqualTo("US");
    }
}