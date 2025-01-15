package io.muun.common.crypto.hd;

import io.muun.common.crypto.hd.exception.InvalidDerivationPathException;

import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class DerivationPathUtilsTest {

    @Test
    public void testTranslateToCanonicalPath() {
        final String canonicalPath =
                DerivationPathUtils.translateToCanonicalPath("m/schema:1'/recovery:1'/qr:1/123");

        assertThat(canonicalPath).isEqualTo("m/1'/1'/1/123");
    }

    @Test
    public void testParsePathValid() {
        final List<ChildNumber> childNumbers =
                DerivationPathUtils.parsePath("client-key/schema:1'/recovery:1'/qr:1/123");

        assertThat(childNumbers).containsExactly(
                new ChildNumber(1, true, "schema"),
                new ChildNumber(1, true, "recovery"),
                new ChildNumber(1, false, "qr"),
                new ChildNumber(123, false, "")
        );
    }

    @Test(expected = InvalidDerivationPathException.class)
    public void testParsePathInvalid() {
        DerivationPathUtils.parsePath("$%^&");
    }

    @Test
    public void testIsValidPath() {
        assertThat(DerivationPathUtils.isValidPath("m/1'/1'")).isTrue();
        assertThat(DerivationPathUtils.isValidPath("client-key/schema:1'/recovery:1'")).isTrue();
        assertThat(DerivationPathUtils.isValidPath("$%^&'")).isFalse();
    }

    @Test
    public void testHandlesPathsWithAlternativeHardenedSuffix() {
        final String path = "m/84h/1h/0h/0/28h";

        final List<ChildNumber> childNumbers = DerivationPathUtils.parsePath(path);

        assertThat(childNumbers).containsExactly(
                new ChildNumber(84, true, ""),
                new ChildNumber(1, true, ""),
                new ChildNumber(0, true, ""),
                new ChildNumber(0, false, ""),
                new ChildNumber(28, true, "")
        );

        assertThat(DerivationPathUtils.isValidPath(path)).isTrue();
    }
}