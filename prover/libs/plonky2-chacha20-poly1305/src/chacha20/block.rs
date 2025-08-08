use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::BoolTarget;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_u32::gadgets::arithmetic_u32::CircuitBuilderU32;
use plonky2_u32::gadgets::arithmetic_u32::U32Target;

use crate::u32_bits::be_bits_to_u32;
use crate::u32_bits::u32_to_be_bits;

const ROUNDS: usize = 20;
pub const BLOCK_SIZE_U32: usize = 16;
pub const BLOCK_SIZE_BYTES: usize = 4 * BLOCK_SIZE_U32;
pub const BLOCK_SIZE_BITS: usize = 8 * BLOCK_SIZE_BYTES;
pub type Block = [U32Target; BLOCK_SIZE_U32];

pub fn chacha20_block<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    input: Block,
) -> Block {
    let mut x = input;

    for _ in (0..ROUNDS).step_by(2) {
        // Odd round
        (x[0], x[4], x[8], x[12]) = quarter_round(builder, x[0], x[4], x[8], x[12]);
        (x[1], x[5], x[9], x[13]) = quarter_round(builder, x[1], x[5], x[9], x[13]);
        (x[2], x[6], x[10], x[14]) = quarter_round(builder, x[2], x[6], x[10], x[14]);
        (x[3], x[7], x[11], x[15]) = quarter_round(builder, x[3], x[7], x[11], x[15]);

        // Even round
        (x[0], x[5], x[10], x[15]) = quarter_round(builder, x[0], x[5], x[10], x[15]);
        (x[1], x[6], x[11], x[12]) = quarter_round(builder, x[1], x[6], x[11], x[12]);
        (x[2], x[7], x[8], x[13]) = quarter_round(builder, x[2], x[7], x[8], x[13]);
        (x[3], x[4], x[9], x[14]) = quarter_round(builder, x[3], x[4], x[9], x[14]);
    }

    x.iter()
        .zip(input.iter())
        .map(|(xi, yi)| add_u32(builder, xi, yi))
        .collect::<Vec<U32Target>>()
        .try_into()
        .unwrap()
}

fn quarter_round<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    mut a: U32Target,
    mut b: U32Target,
    mut c: U32Target,
    mut d: U32Target,
) -> (U32Target, U32Target, U32Target, U32Target) {
    a = add_u32(builder, &a, &b);
    d = xor_u32(builder, d, a);
    d = rotate_left(builder, d, 16);

    c = add_u32(builder, &c, &d);
    b = xor_u32(builder, b, c);
    b = rotate_left(builder, b, 12);

    a = add_u32(builder, &a, &b);
    d = xor_u32(builder, d, a);
    d = rotate_left(builder, d, 8);

    c = add_u32(builder, &c, &d);
    b = xor_u32(builder, b, c);
    b = rotate_left(builder, b, 7);

    (a, b, c, d)
}

fn rotate_left<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a: U32Target,
    n: usize,
) -> U32Target {
    let mut a_bits = u32_to_be_bits(builder, &a);
    a_bits.rotate_left(n);
    be_bits_to_u32(builder, a_bits)
}

fn add_u32<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a: &U32Target,
    b: &U32Target,
) -> U32Target {
    let (res, _carry) = builder.add_u32(*a, *b);
    res
}

fn xor_u32<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a: U32Target,
    b: U32Target,
) -> U32Target {
    let a_bits = u32_to_be_bits(builder, &a);
    let b_bits = u32_to_be_bits(builder, &b);
    let a_xor_b_bits = a_bits
        .iter()
        .zip(b_bits.iter())
        .map(|(x, y)| xor(builder, *x, *y))
        .collect();

    be_bits_to_u32(builder, a_xor_b_bits)
}

pub fn xor<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a: BoolTarget,
    b: BoolTarget,
) -> BoolTarget {
    let s = builder.sub(a.target, b.target);
    BoolTarget::new_unsafe(builder.mul(s, s))
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use plonky2::iop::witness::PartialWitness;
    use plonky2::plonk::circuit_builder::CircuitBuilder;
    use plonky2::plonk::circuit_data::CircuitConfig;
    use plonky2::plonk::config::GenericConfig;
    use plonky2::plonk::config::PoseidonGoldilocksConfig;
    use plonky2_u32::gadgets::arithmetic_u32::CircuitBuilderU32;
    use plonky2_u32::witness::WitnessU32;

    use crate::chacha20::block::Block;
    use crate::chacha20::block::chacha20_block;
    use crate::chacha20::block::quarter_round;

    #[test]
    fn test_quarter_round() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

        let a_in = builder.add_virtual_u32_target();
        let b_in = builder.add_virtual_u32_target();
        let c_in = builder.add_virtual_u32_target();
        let d_in = builder.add_virtual_u32_target();
        let (a_out, b_out, c_out, d_out) = quarter_round(&mut builder, a_in, b_in, c_in, d_in);

        let mut pw = PartialWitness::new();

        // https://www.rfc-editor.org/rfc/rfc8439#section-2.1.1

        pw.set_u32_target(a_in, 0x11111111);
        pw.set_u32_target(b_in, 0x01020304);
        pw.set_u32_target(c_in, 0x9b8d6f43);
        pw.set_u32_target(d_in, 0x01234567);

        pw.set_u32_target(a_out, 0xea2a92f4);
        pw.set_u32_target(b_out, 0xcb1cf8ce);
        pw.set_u32_target(c_out, 0x4581472e);
        pw.set_u32_target(d_out, 0x5881c4bb);

        let data = builder.build::<C>();

        let proof = data.prove(pw)?;

        data.verify(proof)
    }

    #[test]
    fn test_block() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

        let input_block_targets: Block = core::array::from_fn(|_| builder.add_virtual_u32_target());
        let output_block_targets = chacha20_block::<F, D>(&mut builder, input_block_targets);

        let mut pw = PartialWitness::new();

        // https://www.rfc-editor.org/rfc/rfc8439#section-2.3.2

        let input_block_data: [u32; 16] = [
            0x61707865, 0x3320646e, 0x79622d32, 0x6b206574, //
            0x03020100, 0x07060504, 0x0b0a0908, 0x0f0e0d0c, //
            0x13121110, 0x17161514, 0x1b1a1918, 0x1f1e1d1c, //
            0x00000001, 0x09000000, 0x4a000000, 0x00000000, //
        ];
        let output_block_data: [u32; 16] = [
            0xe4e7f110, 0x15593bd1, 0x1fdd0f50, 0xc47120a3, //
            0xc7f4d1c7, 0x0368c033, 0x9aaa2204, 0x4e6cd4c3, //
            0x466482d2, 0x09aa9f07, 0x05d7c214, 0xa2028bd9, //
            0xd19c12b5, 0xb94e16de, 0xe883d0cb, 0x4e3c50a2, //
        ];

        for (target, value) in input_block_targets.iter().zip(input_block_data.iter()) {
            pw.set_u32_target(*target, *value);
        }
        for (target, value) in output_block_targets.iter().zip(output_block_data.iter()) {
            pw.set_u32_target(*target, *value);
        }

        let data = builder.build::<C>();

        let proof = data.prove(pw)?;

        data.verify(proof)
    }
}
