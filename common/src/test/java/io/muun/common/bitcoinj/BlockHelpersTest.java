package io.muun.common.bitcoinj;


import io.muun.common.utils.Encodings;
import io.muun.common.utils.TestUtils;

import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.Block;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class BlockHelpersTest {

    @Test
    public void testGetBlockHeightFromCoinbase() {

        final String rawBlock = "04000000eb6e0f7612d9dc984317f92c8d2a80859af5f13cf66cecbd0b32800000"
                + "00000044cca90e206bed8143433d23be74966a2237817eb2f2c3742242ebca143859f7f6e00f57ff"
                + "ff001d57e24210010100000001000000000000000000000000000000000000000000000000000000"
                + "0000000000ffffffff04038cae0bffffffff04f0e763230000000017a914349ef962198fcc875f45"
                + "e786598272ecace9818d8750d6dc010000000017a914349ef962198fcc875f45e786598272ecace9"
                + "818d870000000000000000226a200000000000000000000000000000000000000000000000000000"
                + "ffff0000000000000000000000000a6a08421091310300000000000000";

        final int expectedHeight = 765580;

        final TestNet3Params params = TestNet3Params.get();
        TestUtils.setBitcoinjContext(params);
        final Block block = new BitcoinSerializer(params, false)
                .makeBlock(Encodings.hexToBytes(rawBlock));

        assertThat(BlockHelpers.getBlockHeightFromCoinbase(block)).isEqualTo(expectedHeight);
    }

    @Test
    public void testCertainty() {
        final List<Integer> results1Block
                = Arrays.asList(64, 134, 215, 307, 416, 550, 723, 966, 1382);
        final List<Integer> results100Block
                = Arrays.asList(52451, 54901, 56715, 58296, 59801, 61331, 62996, 64983, 67807);
        final List<Integer> results1000Block = Arrays.asList(
                575817, 583976, 589908, 595007, 599801, 604619, 609803, 615908, 624441
        );

        final List<Integer> computedArrayFor1Block = new ArrayList<Integer>();
        final List<Integer> computedArrayFor100Block = new ArrayList<Integer>();
        final List<Integer> computedArrayFor1000Block = new ArrayList<Integer>();

        for (int i = 1; i < 10; i++) {
            computedArrayFor1Block.add(
                    BlockHelpers.timeInSecsForBlocksWithCertainty(1, i / 10.)
            );
            computedArrayFor100Block.add(
                    BlockHelpers.timeInSecsForBlocksWithCertainty(100, i / 10.)
            );
            computedArrayFor1000Block.add(
                    BlockHelpers.timeInSecsForBlocksWithCertainty(1000, i / 10.)
            );
        }

        assertThat(results1Block).isEqualTo(computedArrayFor1Block);
        assertThat(results100Block).isEqualTo(computedArrayFor100Block);
        assertThat(results1000Block).isEqualTo(computedArrayFor1000Block);
    }
}