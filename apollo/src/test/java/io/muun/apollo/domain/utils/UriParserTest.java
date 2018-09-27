package io.muun.apollo.domain.utils;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UriParserTest {

    @Test
    public void schemeHost() {
        final UriParser p = new UriParser("bitcoin:address");

        assertThat(p.getScheme()).isEqualTo("bitcoin");
        assertThat(p.getHost()).isEqualTo("address");
        assertThat(p.getPath()).isEmpty();
        assertThat(p.getParams()).isEmpty();
    }

    @Test
    public void schemePath() {
        final UriParser p = new UriParser("bitcoin:/somePath");

        assertThat(p.getScheme()).isEqualTo("bitcoin");
        assertThat(p.getHost()).isEmpty();
        assertThat(p.getPath()).isEqualTo("somePath");
        assertThat(p.getParams()).isEmpty();
    }

    @Test
    public void schemeQuery() {
        final UriParser p = new UriParser("bitcoin:?r=http://foobarbaz?inside=3&outside=5");

        assertThat(p.getScheme()).isEqualTo("bitcoin");
        assertThat(p.getHost()).isEmpty();
        assertThat(p.getPath()).isEmpty();
        assertThat(p.getParams()).containsExactly(
                new NameValuePair("r", "http://foobarbaz?inside=3"),
                new NameValuePair("outside", "5")
        );
    }

    @Test
    public void schemeHostPath() {
        final UriParser p = new UriParser("bitcoin:address/somePath");

        assertThat(p.getScheme()).isEqualTo("bitcoin");
        assertThat(p.getHost()).isEqualTo("address");
        assertThat(p.getPath()).isEqualTo("somePath");
        assertThat(p.getParams()).isEmpty();
    }

    @Test
    public void schemeHostPathQuery() {
        final UriParser p = new UriParser("bitcoin:address/somePath?a=1&b=haha");

        assertThat(p.getScheme()).isEqualTo("bitcoin");
        assertThat(p.getHost()).isEqualTo("address");
        assertThat(p.getPath()).isEqualTo("somePath");
        assertThat(p.getParams()).containsExactly(
                new NameValuePair("a", "1"),
                new NameValuePair("b", "haha")
        );
    }
}
