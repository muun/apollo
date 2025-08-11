use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::BoolTarget;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_u32::gadgets::arithmetic_u32::CircuitBuilderU32;
use plonky2_u32::gadgets::arithmetic_u32::U32Target;

use crate::chacha20::block::Block;
use crate::chacha20::block::chacha20_block;
use crate::u32_bits::le_bits_to_u32;

// The constants specified in https://www.rfc-editor.org/rfc/rfc8439#section-2.3
const CHA_CHA_20_CONSTANTS: [u32; 4] = [0x61707865, 0x3320646e, 0x79622d32, 0x6b206574];

pub fn chacha20_keystream<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    key: [BoolTarget; 256],
    nonce: [BoolTarget; 96],
    initial_counter: u32,
    num_blocks: u32,
) -> Vec<Block> {
    let key_u32_targets: Vec<U32Target> = key
        .chunks_exact(32)
        .map(|chunk| le_bits_to_u32(builder, chunk.to_vec()))
        .collect();

    let nonce_u32_targets: Vec<U32Target> = nonce
        .chunks_exact(32)
        .map(|chunk| le_bits_to_u32(builder, chunk.to_vec()))
        .collect();

    let mut input: Block = [builder.constant_u32(0); 16];

    for i in 0..4 {
        input[i] = builder.constant_u32(CHA_CHA_20_CONSTANTS[i]);
    }
    input[4..12].copy_from_slice(key_u32_targets.as_slice());
    input[13..16].copy_from_slice(nonce_u32_targets.as_slice());

    let mut blocks = Vec::new();

    for i in 0..num_blocks {
        input[12] = builder.constant_u32(initial_counter + i);
        blocks.push(chacha20_block(builder, input));
    }

    blocks
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use itertools::enumerate;
    use plonky2::iop::target::BoolTarget;
    use plonky2::iop::witness::PartialWitness;
    use plonky2::iop::witness::WitnessWrite;
    use plonky2::plonk::circuit_builder::CircuitBuilder;
    use plonky2::plonk::circuit_data::CircuitConfig;
    use plonky2::plonk::config::GenericConfig;
    use plonky2::plonk::config::PoseidonGoldilocksConfig;
    use plonky2_u32::witness::WitnessU32;

    use crate::chacha20::keystream::chacha20_keystream;

    struct TestData {
        pub key_bytes: [u8; 32],
        pub nonce_bytes: [u8; 12],
        pub initial_counter: u32,
        pub expected_output_first_block: [u8; 64],
    }

    #[test]
    fn test_keystream() {
        for t in [
            TestData {
                // https://www.rfc-editor.org/rfc/rfc8439#section-2.3.2
                key_bytes: [
                    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, //
                    0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, //
                    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, //
                    0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, //
                ],
                nonce_bytes: [
                    0x00, 0x00, 0x00, 0x09, //
                    0x00, 0x00, 0x00, 0x4a, //
                    0x00, 0x00, 0x00, 0x00, //
                ],
                initial_counter: 1,
                expected_output_first_block: [
                    0x10, 0xf1, 0xe7, 0xe4, 0xd1, 0x3b, 0x59, 0x15, //
                    0x50, 0x0f, 0xdd, 0x1f, 0xa3, 0x20, 0x71, 0xc4, //
                    0xc7, 0xd1, 0xf4, 0xc7, 0x33, 0xc0, 0x68, 0x03, //
                    0x04, 0x22, 0xaa, 0x9a, 0xc3, 0xd4, 0x6c, 0x4e, //
                    0xd2, 0x82, 0x64, 0x46, 0x07, 0x9f, 0xaa, 0x09, //
                    0x14, 0xc2, 0xd7, 0x05, 0xd9, 0x8b, 0x02, 0xa2, //
                    0xb5, 0x12, 0x9c, 0xd1, 0xde, 0x16, 0x4e, 0xb9, //
                    0xcb, 0xd0, 0x83, 0xe8, 0xa2, 0x50, 0x3c, 0x4e, //
                ],
            },
            TestData {
                // https://www.rfc-editor.org/rfc/rfc8439#section-2.4.2, first block
                key_bytes: [
                    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, //
                    0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, //
                    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, //
                    0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, //
                ],
                nonce_bytes: [
                    0x00, 0x00, 0x00, 0x00, //
                    0x00, 0x00, 0x00, 0x4a, //
                    0x00, 0x00, 0x00, 0x00, //
                ],
                initial_counter: 1,
                expected_output_first_block: [
                    0x22, 0x4f, 0x51, 0xf3, 0x40, 0x1b, 0xd9, 0xe1, //
                    0x2f, 0xde, 0x27, 0x6f, 0xb8, 0x63, 0x1d, 0xed, //
                    0x8c, 0x13, 0x1f, 0x82, 0x3d, 0x2c, 0x06, 0xe2, //
                    0x7e, 0x4f, 0xca, 0xec, 0x9e, 0xf3, 0xcf, 0x78, //
                    0x8a, 0x3b, 0x0a, 0xa3, 0x72, 0x60, 0x0a, 0x92, //
                    0xb5, 0x79, 0x74, 0xcd, 0xed, 0x2b, 0x93, 0x34, //
                    0x79, 0x4c, 0xba, 0x40, 0xc6, 0x3e, 0x34, 0xcd, //
                    0xea, 0x21, 0x2c, 0x4c, 0xf0, 0x7d, 0x41, 0xb7, //
                ],
            },
            TestData {
                // https://www.rfc-editor.org/rfc/rfc8439#section-2.4.2, second block
                key_bytes: [
                    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, //
                    0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, //
                    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, //
                    0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, //
                ],
                nonce_bytes: [
                    0x00, 0x00, 0x00, 0x00, //
                    0x00, 0x00, 0x00, 0x4a, //
                    0x00, 0x00, 0x00, 0x00, //
                ],
                initial_counter: 2,
                expected_output_first_block: [
                    0x69, 0xa6, 0x74, 0x9f, 0x3f, 0x63, 0x0f, 0x41, //
                    0x22, 0xca, 0xfe, 0x28, 0xec, 0x4d, 0xc4, 0x7e, //
                    0x26, 0xd4, 0x34, 0x6d, 0x70, 0xb9, 0x8c, 0x73, //
                    0xf3, 0xe9, 0xc5, 0x3a, 0xc4, 0x0c, 0x59, 0x45, //
                    0x39, 0x8b, 0x6e, 0xda, 0x1a, 0x83, 0x2c, 0x89, //
                    0xc1, 0x67, 0xea, 0xcd, 0x90, 0x1d, 0x7e, 0x2b, //
                    0xf3, 0x63, 0x74, 0x03, 0x73, 0x20, 0x1a, 0xa1, //
                    0x88, 0xfb, 0xbc, 0xe8, 0x39, 0x91, 0xc4, 0xed, //
                ],
            },
        ] {
            test_chacha20_keystream_for_fixed_inputs(t).expect("test failed");
        }
    }
    fn test_chacha20_keystream_for_fixed_inputs(t: TestData) -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

        let key_targets: [BoolTarget; 256] =
            core::array::from_fn(|_| builder.add_virtual_bool_target_unsafe());

        let nonce_targets: [BoolTarget; 96] =
            core::array::from_fn(|_| builder.add_virtual_bool_target_unsafe());

        let blocks = chacha20_keystream::<F, D>(
            &mut builder,
            key_targets,
            nonce_targets,
            t.initial_counter,
            1,
        );

        let mut pw = PartialWitness::new();

        for i in 0..t.key_bytes.len() {
            let byte = t.key_bytes[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(key_targets[8 * i + j], bit)?;
            }
        }

        for i in 0..t.nonce_bytes.len() {
            let byte = t.nonce_bytes[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(nonce_targets[8 * i + j], bit)?;
            }
        }

        for (i, chunk) in enumerate(t.expected_output_first_block.chunks_exact(4)) {
            let entry = u32::from_le_bytes(chunk.try_into()?);
            pw.set_u32_target(blocks[0][i], entry);
        }

        let data = builder.build::<C>();

        let proof = data.prove(pw)?;

        data.verify(proof)
    }
}
