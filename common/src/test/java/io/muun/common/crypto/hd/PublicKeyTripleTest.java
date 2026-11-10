package io.muun.common.crypto.hd;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PublicKeyTripleTest {

    private static final NetworkParameters PARAMS = MainNetParams.get();

    private static final String BASE_PATH = "m/schema:1'/recovery:1'";

    private PublicKey userPublicKey;
    private PublicKey muunPublicKey;
    private PublicKey lightningServerPublicKey;

    @Before
    public void setUp() {
        Context.propagate(new Context(PARAMS));

        userPublicKey = PrivateKey.getNewRootPrivateKey(PARAMS)
                .deriveFromAbsolutePath(BASE_PATH)
                .getPublicKey();

        muunPublicKey = PrivateKey.getNewRootPrivateKey(PARAMS)
                .deriveFromAbsolutePath(BASE_PATH)
                .getPublicKey();

        // The lightning server key reaches Houston re-rooted at m/, unlike user/muun keys.
        lightningServerPublicKey = PrivateKey.getNewRootPrivateKey(PARAMS).getPublicKey();
    }

    @Test
    public void deriveFromAbsolutePathMapsRelativePathOntoLightningServerBase() {

        final PublicKeyTriple triple = buildTriple();

        final PublicKeyTriple derived = triple.deriveFromAbsolutePath(BASE_PATH + "/change:0/5");

        assertThat(derived.getUserPublicKey().getAbsoluteDerivationPath())
                .isEqualTo(BASE_PATH + "/change:0/5");
        assertThat(derived.getMuunPublicKey().getAbsoluteDerivationPath())
                .isEqualTo(BASE_PATH + "/change:0/5");
        assertThat(derived.getLightningServerPublicKey().getAbsoluteDerivationPath())
                .isEqualTo("m/change:0/5");

        // The peer key must land at the same relative position as if derived directly from m/.
        assertThat(derived.getLightningServerPublicKey())
                .isEqualTo(lightningServerPublicKey.deriveFromAbsolutePath("m/change:0/5"));
    }

    @Test
    public void deriveFromAbsolutePathAtBasePathKeepsLightningServerAtRoot() {

        final PublicKeyTriple triple = buildTriple();

        final PublicKeyTriple derived = triple.deriveFromAbsolutePath(BASE_PATH);

        assertThat(derived.getUserPublicKey().getAbsoluteDerivationPath()).isEqualTo(BASE_PATH);
        assertThat(derived.getLightningServerPublicKey().getAbsoluteDerivationPath())
                .isEqualTo("m");
    }

    @Test
    public void deriveChildKeepsAllThreeKeysAtMatchingRelativePositions() {

        // Same shape as address derivation: branch first, then a child index.
        final PublicKeyTriple triple = buildTriple()
                .deriveFromAbsolutePath(Schema.getChangeKeyPath());

        final PublicKeyTriple child = triple.deriveChild(7);

        assertThat(child.getUserPublicKey().getAbsoluteDerivationPath())
                .isEqualTo(BASE_PATH + "/change:0/7");
        assertThat(child.getMuunPublicKey().getAbsoluteDerivationPath())
                .isEqualTo(BASE_PATH + "/change:0/7");
        assertThat(child.getLightningServerPublicKey().getAbsoluteDerivationPath())
                .isEqualTo("m/change:0/7");

        assertThat(child.getUserPublicKey().getLastLevelIndex()).isEqualTo(7);
        assertThat(child.getMuunPublicKey().getLastLevelIndex()).isEqualTo(7);
        assertThat(child.getLightningServerPublicKey().getLastLevelIndex()).isEqualTo(7);
    }

    @Test
    public void deriveFromAbsolutePathRejectsPathsOutsideTheUserBase() {

        final PublicKeyTriple triple = buildTriple();

        assertThatThrownBy(() -> triple.deriveFromAbsolutePath("m/other:2'/branch:1'/change:0/5"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void deriveFromAbsolutePathAcceptsEquivalentPathSpellings() {
        final PublicKeyTriple triple = buildTriple();
        final PublicKeyTriple expected = triple.deriveFromAbsolutePath(
                Schema.getExternalKeyPath() + "/7"
        );

        for (String path : new String[]{
                "m/1'/1'/1/7",
                "m/layout:1'/backup:1'/external:1/7",
                "wallet/layout:1h/backup:1h/external:1/7"
        }) {
            final PublicKeyTriple derived = triple.deriveFromAbsolutePath(path);
            assertThat(derived.getUserPublicKey().serializeBase58())
                    .isEqualTo(expected.getUserPublicKey().serializeBase58());
            assertThat(derived.getCosignerPublicKey().serializeBase58())
                    .isEqualTo(expected.getCosignerPublicKey().serializeBase58());
            assertThat(derived.getLightningServerPublicKey().serializeBase58())
                    .isEqualTo(expected.getLightningServerPublicKey().serializeBase58());
        }
    }

    @Test
    public void deriveFromCanonicalBasePathKeepsLightningServerAtRoot() {
        final PublicKeyTriple derived = buildTriple().deriveFromAbsolutePath("m/1'/1'");

        assertThat(derived.getUserPublicKey().serializeBase58())
                .isEqualTo(userPublicKey.serializeBase58());
        assertThat(derived.getLightningServerPublicKey().serializeBase58())
                .isEqualTo(lightningServerPublicKey.serializeBase58());
        assertThat(derived.getLightningServerPublicKey().getAbsoluteDerivationPath())
                .isEqualTo("m");
    }

    @Test
    public void deriveFromAbsolutePathRejectsAncestorsAndDifferentHardenedSteps() {
        final PublicKeyTriple triple = buildTriple();

        assertThatThrownBy(() -> triple.deriveFromAbsolutePath("m/1'"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> triple.deriveFromAbsolutePath("m/1'/1/1/7"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PublicKeyTriple buildTriple() {
        return new PublicKeyTriple(
                userPublicKey,
                muunPublicKey,
                lightningServerPublicKey
        );
    }
}
