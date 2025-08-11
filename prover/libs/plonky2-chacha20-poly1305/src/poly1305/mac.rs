use num_bigint::BigUint;
use num_traits::Num;
use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::BoolTarget;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_ecdsa::gadgets::biguint::BigUintTarget;
use plonky2_ecdsa::gadgets::biguint::CircuitBuilderBiguint;

use crate::u32_bits::le_bits_to_u32;
use crate::u32_bits::u32_to_le_bits;

// p = 2^130 - 5, see https://www.rfc-editor.org/rfc/rfc8439#section-2.5
const P_HEX: &str = "3fffffffffffffffffffffffffffffffb";

pub fn poly1305_mac<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    key: [BoolTarget; 256],
    m: Vec<BoolTarget>,
) -> [BoolTarget; 128] {
    let r = compute_r(builder, key);
    let s = compute_s(builder, key);

    let p = compute_p(builder);

    let mut accumulator = builder.zero_biguint();

    for coefficient in message_coefficients(builder, m) {
        accumulator = builder.add_biguint(&accumulator, &coefficient);
        accumulator = builder.mul_biguint(&accumulator, &r);
        accumulator = builder.rem_biguint(&accumulator, &p);
    }

    accumulator = builder.add_biguint(&accumulator, &s);

    assert!(accumulator.limbs.len() >= 4);

    accumulator
        .limbs
        .iter()
        .take(4)
        .flat_map(|limb| u32_to_le_bits(builder, limb))
        .collect::<Vec<BoolTarget>>()
        .try_into()
        .unwrap()
}

// p is 5 limbs long
fn compute_p<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
) -> BigUintTarget {
    builder.constant_biguint(&BigUint::from_str_radix(P_HEX, 16).unwrap())
}

// see https://www.rfc-editor.org/rfc/rfc8439#section-2.5
fn clamp<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    r: [BoolTarget; 128],
) -> [BoolTarget; 128] {
    let mut r = r;

    for i in [3, 7, 11, 15] {
        r[8 * i + 4] = builder.constant_bool(false);
        r[8 * i + 5] = builder.constant_bool(false);
        r[8 * i + 6] = builder.constant_bool(false);
        r[8 * i + 7] = builder.constant_bool(false);
    }

    for i in [4, 8, 12] {
        r[8 * i] = builder.constant_bool(false);
        r[8 * i + 1] = builder.constant_bool(false);
    }

    r
}

// r is 4 limbs long
fn compute_r<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    key: [BoolTarget; 256],
) -> BigUintTarget {
    let (r, _s) = key.split_at(128);
    let limbs = clamp(builder, r.try_into().unwrap())
        .chunks_exact(32)
        .map(|chunk| le_bits_to_u32(builder, chunk.into()))
        .collect();

    BigUintTarget { limbs }
}

// s is 4 limbs long
fn compute_s<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    key: [BoolTarget; 256],
) -> BigUintTarget {
    let (_r, s) = key.split_at(128);
    let limbs = s
        .chunks_exact(32)
        .map(|chunk| le_bits_to_u32(builder, chunk.to_vec()))
        .collect();

    BigUintTarget { limbs }
}
fn message_coefficients<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    m: Vec<BoolTarget>,
) -> Vec<BigUintTarget> {
    m.chunks(16 * 8)
        .map(|chunk| chunk_to_coefficient(builder, chunk))
        .collect()
}

fn chunk_to_coefficient<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    chunk: &[BoolTarget],
) -> BigUintTarget {
    let mut appended_chunk = chunk.to_vec();
    appended_chunk.push(builder.constant_bool(true));
    while appended_chunk.len() % 32 != 0 {
        appended_chunk.push(builder.constant_bool(false));
    }
    let limbs = appended_chunk
        .chunks_exact(32)
        .map(|chunk| le_bits_to_u32(builder, chunk.to_vec()))
        .collect::<Vec<_>>();
    BigUintTarget { limbs }
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

    use crate::poly1305::mac::clamp;
    use crate::poly1305::mac::compute_r;
    use crate::poly1305::mac::compute_s;
    use crate::poly1305::mac::poly1305_mac;

    #[test]
    fn test_clamp() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

        let before_targets: [BoolTarget; 128] =
            core::array::from_fn(|_| builder.add_virtual_bool_target_unsafe());

        let after_targets: [BoolTarget; 128] = clamp(&mut builder, before_targets);

        let mut pw = PartialWitness::new();

        // https://www.rfc-editor.org/rfc/rfc8439#section-2.5.2

        let before_values: [u8; 16] = [
            0x85, 0xd6, 0xbe, 0x78, 0x57, 0x55, 0x6d, 0x33, //
            0x7f, 0x44, 0x52, 0xfe, 0x42, 0xd5, 0x06, 0xa8, //
        ];

        let after_values: [u8; 16] = [
            0x85, 0xd6, 0xbe, 0x08, 0x54, 0x55, 0x6d, 0x03, //
            0x7c, 0x44, 0x52, 0x0e, 0x40, 0xd5, 0x06, 0x08, //
        ];

        for i in 0..before_values.len() {
            let byte = before_values[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(before_targets[8 * i + j], bit)?;
            }
        }

        for i in 0..after_values.len() {
            let byte = after_values[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(after_targets[8 * i + j], bit)?;
            }
        }

        let data = builder.build::<C>();

        let proof = data.prove(pw)?;

        data.verify(proof)
    }

    #[test]
    fn test_compute_r() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

        let key_targets: [BoolTarget; 256] =
            core::array::from_fn(|_| builder.add_virtual_bool_target_unsafe());

        let r_targets = compute_r(&mut builder, key_targets);

        if r_targets.limbs.len() != 4 {
            panic!("r_targets length mismatch");
        }

        let mut pw = PartialWitness::new();

        let key: [u8; 32] = [
            0x85, 0xd6, 0xbe, 0x78, 0x57, 0x55, 0x6d, 0x33, //
            0x7f, 0x44, 0x52, 0xfe, 0x42, 0xd5, 0x06, 0xa8, //
            0x01, 0x03, 0x80, 0x8a, 0xfb, 0x0d, 0xb2, 0xfd, //
            0x4a, 0xbf, 0xf6, 0xaf, 0x41, 0x49, 0xf5, 0x1b, //
        ];

        for i in 0..key.len() {
            let byte = key[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(key_targets[8 * i + j], bit)?;
            }
        }

        let r_limbs: [u32; 4] = [0x08bed685, 0x036d5554, 0x0e52447c, 0x0806d540];
        for (i, limb) in enumerate(r_limbs) {
            pw.set_u32_target(r_targets.limbs[i], limb);
        }

        let data = builder.build::<C>();

        let proof = data.prove(pw)?;

        data.verify(proof)
    }

    #[test]
    fn test_compute_s() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

        let key_targets: [BoolTarget; 256] =
            core::array::from_fn(|_| builder.add_virtual_bool_target_unsafe());

        let s_targets = compute_s(&mut builder, key_targets);

        if s_targets.limbs.len() != 4 {
            panic!("s_targets length mismatch");
        }

        let mut pw = PartialWitness::new();

        let key: [u8; 32] = [
            0x85, 0xd6, 0xbe, 0x78, 0x57, 0x55, 0x6d, 0x33, //
            0x7f, 0x44, 0x52, 0xfe, 0x42, 0xd5, 0x06, 0xa8, //
            0x01, 0x03, 0x80, 0x8a, 0xfb, 0x0d, 0xb2, 0xfd, //
            0x4a, 0xbf, 0xf6, 0xaf, 0x41, 0x49, 0xf5, 0x1b, //
        ];

        for i in 0..key.len() {
            let byte = key[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(key_targets[8 * i + j], bit)?;
            }
        }

        let s_limbs: [u32; 4] = [0x8a800301, 0xfdb20dfb, 0xaff6bf4a, 0x1bf54941];
        for (i, limb) in enumerate(s_limbs) {
            pw.set_u32_target(s_targets.limbs[i], limb);
        }

        let data = builder.build::<C>();

        let proof = data.prove(pw)?;

        data.verify(proof)
    }

    #[test]
    fn test_poly1305_mac() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

        let key_targets: [BoolTarget; 256] =
            core::array::from_fn(|_| builder.add_virtual_bool_target_unsafe());

        // https://www.rfc-editor.org/rfc/rfc8439#section-2.5.2

        let message = "Cryptographic Forum Research Group".as_bytes().to_vec();

        let message_targets: Vec<_> = (0..8 * message.len())
            .map(|_| builder.add_virtual_bool_target_unsafe())
            .collect();

        let tag_targets = poly1305_mac(&mut builder, key_targets, message_targets.clone());

        let mut pw = PartialWitness::new();

        let key: [u8; 32] = [
            0x85, 0xd6, 0xbe, 0x78, 0x57, 0x55, 0x6d, 0x33, //
            0x7f, 0x44, 0x52, 0xfe, 0x42, 0xd5, 0x06, 0xa8, //
            0x01, 0x03, 0x80, 0x8a, 0xfb, 0x0d, 0xb2, 0xfd, //
            0x4a, 0xbf, 0xf6, 0xaf, 0x41, 0x49, 0xf5, 0x1b, //
        ];

        let tag: [u8; 16] = [
            0xa8, 0x06, 0x1d, 0xc1, 0x30, 0x51, 0x36, 0xc6, //
            0xc2, 0x2b, 0x8b, 0xaf, 0x0c, 0x01, 0x27, 0xa9, //
        ];

        for i in 0..key.len() {
            let byte = key[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(key_targets[8 * i + j], bit)?;
            }
        }

        for i in 0..message.len() {
            let byte = message[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(message_targets[8 * i + j], bit)?;
            }
        }

        for i in 0..tag.len() {
            let byte = tag[i];
            for j in 0..8 {
                let bit = ((byte >> j) & 1) != 0;
                pw.set_bool_target(tag_targets[8 * i + j], bit)?;
            }
        }

        let data = builder.build::<C>();

        let proof = data.prove(pw)?;

        data.verify(proof)
    }
}
