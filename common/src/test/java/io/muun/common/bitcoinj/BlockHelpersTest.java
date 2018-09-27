package io.muun.common.bitcoinj;


import io.muun.common.utils.Encodings;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Context;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class BlockHelpersTest {

    @Test
    public void testGetBlockHeightFromCoinbase() throws Exception {

        final String rawBlock = "04000000eb6e0f7612d9dc984317f92c8d2a80859af5f13cf66cecbd0b32800000"
                + "00000044cca90e206bed8143433d23be74966a2237817eb2f2c3742242ebca143859f7f6e00f57ff"
                + "ff001d57e24210010100000001000000000000000000000000000000000000000000000000000000"
                + "0000000000ffffffff04038cae0bffffffff04f0e763230000000017a914349ef962198fcc875f45"
                + "e786598272ecace9818d8750d6dc010000000017a914349ef962198fcc875f45e786598272ecace9"
                + "818d870000000000000000226a200000000000000000000000000000000000000000000000000000"
                + "ffff0000000000000000000000000a6a08421091310300000000000000";

        final int expectedHeight = 765580;

        Context.getOrCreate(TestNet3Params.get());
        final Block block = new Block(TestNet3Params.get(), Encodings.hexToBytes(rawBlock));

        assertThat(BlockHelpers.getBlockHeightFromCoinbase(block)).isEqualTo(expectedHeight);
    }

}