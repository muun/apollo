package io.muun.common.crypto.hd;

import io.muun.common.crypto.hd.exception.InvalidDerivationPathException;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

public class SchemaTest {

    @Test
    public void validateAddressDerivationPathAcceptsAddressBranches() {
        assertThatCode(() -> Schema.validateAddressDerivationPath(
                Schema.getChangeKeyPath() + "/0")).doesNotThrowAnyException();
        assertThatCode(() -> Schema.validateAddressDerivationPath(
                Schema.getExternalKeyPath() + "/7")).doesNotThrowAnyException();
    }

    // Comments are cosmetic, so the canonical form of an address path is just as valid.
    @Test
    public void validateAddressDerivationPathAcceptsCanonicalPaths() {
        assertThatCode(() -> Schema.validateAddressDerivationPath(
                DerivationPathUtils.translateToCanonicalPath(Schema.getChangeKeyPath() + "/0"))
        ).doesNotThrowAnyException();
    }

    // The descriptors cover the change and external branches only.
    @Test
    public void validateAddressDerivationPathRejectsOtherBranches() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> Schema.validateAddressDerivationPath(Schema.getContactsKeyPath() + "/0"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> Schema.validateAddressDerivationPath(Schema.getMetadataKeyPath() + "/0"));
    }

    // The descriptors end in a single `/*`, so no other depth conforms.
    @Test
    public void validateAddressDerivationPathRejectsOtherDepths() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> Schema.validateAddressDerivationPath(Schema.getExternalKeyPath()));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> Schema.validateAddressDerivationPath(
                        Schema.getExternalKeyPath() + "/0/0"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> Schema.validateAddressDerivationPath("m/1/0"));
    }

    // A `/*` range is never hardened, and public keys could not derive it anyway.
    @Test
    public void validateAddressDerivationPathRejectsHardenedIndexes() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> Schema.validateAddressDerivationPath(
                        Schema.getExternalKeyPath() + "/0'"));
    }

    @Test
    public void validateAddressDerivationPathRejectsMalformedPaths() {
        assertThatExceptionOfType(InvalidDerivationPathException.class).isThrownBy(
                () -> Schema.validateAddressDerivationPath("not a path"));
    }
}
