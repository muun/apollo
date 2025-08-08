use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::BoolTarget;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_u32::gadgets::arithmetic_u32::CircuitBuilderU32;
use plonky2_u32::gadgets::arithmetic_u32::U32Target;

// This code is optimized to minimize the number of conversions between bits and u32. For this
// reason some of the functions below take inputs represented as &[BoolTarget] and return U32Target.

#[rustfmt::skip]
pub const H256: [u32; 8] = [
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
];

/// Constants necessary for SHA-256 family of digests.
#[rustfmt::skip]
pub const K256: [u32; 64] = [
    0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5,
    0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
    0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3,
    0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
    0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC,
    0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
    0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7,
    0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
    0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
    0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
    0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3,
    0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
    0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
    0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
    0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208,
    0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2
];

pub type Sha256DigestTarget = [BoolTarget; 256];

pub fn array_to_bits(bytes: &[u8]) -> Vec<bool> {
    let mut ret = Vec::new();
    for byte in bytes {
        for j in 0..8 {
            let bit = (byte >> (7 - j)) & 1u8;
            ret.push(bit == 1u8);
        }
    }
    ret
}

pub fn u32_to_bits_target<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a: &U32Target,
) -> Vec<BoolTarget> {
    let mut res = Vec::new();
    let bit_targets = builder.split_le_base::<2>(a.0, 32);
    for i in (0..32).rev() {
        res.push(BoolTarget::new_unsafe(bit_targets[i]));
    }
    res
}

pub fn bits_to_u32_target<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    bits_target: Vec<BoolTarget>,
) -> U32Target {
    let bit_len = bits_target.len();
    assert_eq!(bit_len, 32);
    U32Target(builder.le_sum(bits_target[0..32].iter().rev()))
}

// ROTATE(x, y) = (((x)>>(y)) | ((x)<<(32-(y))))
fn rotate32(y: usize) -> Vec<usize> {
    let mut res = Vec::new();
    for i in 32 - y..32 {
        res.push(i);
    }
    for i in 0..32 - y {
        res.push(i);
    }
    res
}

// x>>y
// Assume: 0 at index 32
fn shift32(y: usize) -> Vec<usize> {
    let mut res = vec![32; y];
    for i in 0..32 - y {
        res.push(i);
    }
    res
}

fn xor3<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a: BoolTarget,
    b: BoolTarget,
    c: BoolTarget,
) -> BoolTarget {
    let a_plus_b_plus_c = builder.add_many([a.target, b.target, c.target]);
    let x = vec![builder.zero(), builder.one(), builder.zero(), builder.one()];
    BoolTarget::new_unsafe(builder.random_access(a_plus_b_plus_c, x))
}

// Sigma0(x) = (ROTATE((x), 2) ^ ROTATE((x),13) ^ ROTATE((x),22))
fn big_sigma0<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a_bits: &[BoolTarget],
) -> U32Target {
    let rotate2 = rotate32(2);
    let rotate13 = rotate32(13);
    let rotate22 = rotate32(22);
    let mut res_bits = Vec::new();
    for i in 0..32 {
        res_bits.push(xor3(
            builder,
            a_bits[rotate2[i]],
            a_bits[rotate13[i]],
            a_bits[rotate22[i]],
        ));
    }
    bits_to_u32_target(builder, res_bits)
}

// Sigma1(x) = (ROTATE((x), 6) ^ ROTATE((x),11) ^ ROTATE((x),25))
fn big_sigma1<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a_bits: &[BoolTarget],
) -> U32Target {
    let rotate6 = rotate32(6);
    let rotate11 = rotate32(11);
    let rotate25 = rotate32(25);
    let mut res_bits = Vec::new();
    for i in 0..32 {
        res_bits.push(xor3(
            builder,
            a_bits[rotate6[i]],
            a_bits[rotate11[i]],
            a_bits[rotate25[i]],
        ));
    }
    bits_to_u32_target(builder, res_bits)
}

// sigma0(x) = (ROTATE((x), 7) ^ ROTATE((x),18) ^ ((x)>> 3))
fn sigma0<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    mut a_bits: Vec<BoolTarget>,
) -> U32Target {
    a_bits.push(builder.constant_bool(false));
    let rotate7 = rotate32(7);
    let rotate18 = rotate32(18);
    let shift3 = shift32(3);
    let mut res_bits = Vec::new();
    for i in 0..32 {
        res_bits.push(xor3(
            builder,
            a_bits[rotate7[i]],
            a_bits[rotate18[i]],
            a_bits[shift3[i]],
        ));
    }
    bits_to_u32_target(builder, res_bits)
}

// sigma1(x) = (ROTATE((x),17) ^ ROTATE((x),19) ^ ((x)>>10))
fn sigma1<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    mut a_bits: Vec<BoolTarget>,
) -> U32Target {
    a_bits.push(builder.constant_bool(false));
    let rotate17 = rotate32(17);
    let rotate19 = rotate32(19);
    let shift10 = shift32(10);
    let mut res_bits = Vec::new();
    for i in 0..32 {
        res_bits.push(xor3(
            builder,
            a_bits[rotate17[i]],
            a_bits[rotate19[i]],
            a_bits[shift10[i]],
        ));
    }
    bits_to_u32_target(builder, res_bits)
}

// ch(a, b, c) = a&b ^ (!a)&c
fn ch<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a_bits: &[BoolTarget],
    b_bits: &[BoolTarget],
    c_bits: &[BoolTarget],
) -> U32Target {
    // a & b ^ (!a) & c = a * b + (1-a) * c
    //                  = a * (b - c) + c
    let mut res_bits = Vec::new();
    for i in 0..32 {
        let b_sub_c = builder.sub(b_bits[i].target, c_bits[i].target);
        let a_mul_b_sub_c_add_c = builder.mul_add(a_bits[i].target, b_sub_c, c_bits[i].target);
        res_bits.push(BoolTarget::new_unsafe(a_mul_b_sub_c_add_c));
    }
    bits_to_u32_target(builder, res_bits)
}

fn maj<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a_bits: &[BoolTarget],
    b_bits: &[BoolTarget],
    c_bits: &[BoolTarget],
) -> U32Target {
    let mut res_bits = Vec::new();
    for i in 0..32 {
        res_bits.push(maj_bool_target(builder, a_bits[i], b_bits[i], c_bits[i]));
    }
    bits_to_u32_target(builder, res_bits)
}

// majority is True if most of the inputs are True
fn maj_bool_target<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a: BoolTarget,
    b: BoolTarget,
    c: BoolTarget,
) -> BoolTarget {
    let a_plus_b_plus_c = builder.add_many([a.target, b.target, c.target]);
    let m = vec![builder.zero(), builder.zero(), builder.one(), builder.one()];
    BoolTarget::new_unsafe(builder.random_access(a_plus_b_plus_c, m))
}

fn add_u32<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    a: U32Target,
    b: U32Target,
) -> U32Target {
    let (res, _carry) = builder.add_u32(a, b);
    res
}

fn add_many_u32<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    addends: &[U32Target],
) -> U32Target {
    let (res, _carry) = builder.add_many_u32(addends);
    res
}

// padded_msg_len = block_count x 512 bits
// Size: msg_len_in_bits (L) |  p bits   | 64 bits
// Bits:      msg            | 100...000 |    L
pub fn sha256<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    message: &[BoolTarget],
) -> Sha256DigestTarget {
    let msg_len_in_bits = message.len();
    let block_count = (msg_len_in_bits + 65).div_ceil(512);
    let padded_msg_len = 512 * block_count;
    let p = padded_msg_len - 64 - msg_len_in_bits;
    assert!(p > 1);

    let mut padded_message = message.to_vec();
    padded_message.push(builder.constant_bool(true));
    for _ in 0..p - 1 {
        padded_message.push(builder.constant_bool(false));
    }
    for i in 0..64 {
        let b = (msg_len_in_bits >> (63 - i)) & 1;
        padded_message.push(builder.constant_bool(b == 1));
    }

    // init state
    let mut state = H256.map(|h| builder.constant_u32(h)).to_vec();

    for block_index in 0..block_count {
        let mut w: Vec<U32Target> = Vec::new();
        let mut w_bits: Vec<Vec<BoolTarget>> = Vec::new();
        // The compression main loop only uses w (it does not use w_bits). However, computing w_bits
        // in advance is useful to avoid recomputing it when computing w[i] for i between 16 and 64.
        for i in 0..16 {
            let index = block_index * 512 + i * 32;
            let w_i = padded_message[index..index + 32].to_vec();
            w_bits.push(w_i.clone());
            w.push(bits_to_u32_target(builder, w_i));
        }

        for i in 16..64 {
            let s0 = sigma0(builder, w_bits[i - 15].clone());
            let s1 = sigma1(builder, w_bits[i - 2].clone());
            let w_i = add_many_u32(builder, &[s1, w[i - 7], s0, w[i - 16]]);
            w.push(w_i);
            // Since we look 2 positions back to compute w_i, we only need w_bits up to 61 which is
            // used to compute w[63].
            if i < 62 {
                w_bits.push(u32_to_bits_target(builder, &w_i))
            }
        }

        let mut a = state[0];
        let mut b = state[1];
        let mut c = state[2];
        let mut d = state[3];
        let mut e = state[4];
        let mut f = state[5];
        let mut g = state[6];
        let mut h = state[7];

        // For the first iteration we deliberately compute the bits outside the compression main
        // loop. The purpose is to recycle as much as possible at the end of the compression loop.
        let mut a_bits = u32_to_bits_target(builder, &a);
        let mut b_bits = u32_to_bits_target(builder, &b);
        let mut c_bits = u32_to_bits_target(builder, &c);
        let mut e_bits = u32_to_bits_target(builder, &e);
        let mut f_bits = u32_to_bits_target(builder, &f);
        let mut g_bits = u32_to_bits_target(builder, &g);

        for i in 0..64 {
            let big_s1_e = big_sigma1(builder, &e_bits);
            let ch_e_f_g = ch(builder, &e_bits, &f_bits, &g_bits);
            let k256_i = builder.constant_u32(K256[i]);
            let t1 = add_many_u32(builder, &[h, big_s1_e, ch_e_f_g, k256_i, w[i]]);
            let big_s0_a = big_sigma0(builder, &a_bits);
            let maj_a_b_c = maj(builder, &a_bits, &b_bits, &c_bits);

            // We avoid computing t2 because using add_many_u32 is more efficient.

            h = g;
            g = f;
            f = e;
            e = add_u32(builder, d, t1);
            d = c;
            c = b;
            b = a;
            a = add_many_u32(builder, &[t1, big_s0_a, maj_a_b_c]);

            // Now we recycle the bit decompositions as much as possible to avoid recompute.
            // Note that we only need to do this for i<63 because once we exit the compression main
            // loop we don't need the bit decompositions anymore.
            if i < 63 {
                g_bits = f_bits.clone();
                f_bits = e_bits.clone();
                e_bits = u32_to_bits_target(builder, &e);
                c_bits = b_bits.clone();
                b_bits = a_bits.clone();
                a_bits = u32_to_bits_target(builder, &a);
            }
        }

        state[0] = add_u32(builder, state[0], a);
        state[1] = add_u32(builder, state[1], b);
        state[2] = add_u32(builder, state[2], c);
        state[3] = add_u32(builder, state[3], d);
        state[4] = add_u32(builder, state[4], e);
        state[5] = add_u32(builder, state[5], f);
        state[6] = add_u32(builder, state[6], g);
        state[7] = add_u32(builder, state[7], h);
    }

    let mut digest: Vec<BoolTarget> = Vec::new();
    for item in state.iter().take(8) {
        let bit_targets = u32_to_bits_target(builder, item);
        digest.extend(bit_targets);
    }

    digest.try_into().unwrap()
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use plonky2::iop::target::BoolTarget;
    use plonky2::iop::witness::PartialWitness;
    use plonky2::iop::witness::WitnessWrite;
    use plonky2::plonk::circuit_builder::CircuitBuilder;
    use plonky2::plonk::circuit_data::CircuitConfig;
    use plonky2::plonk::config::GenericConfig;
    use plonky2::plonk::config::PoseidonGoldilocksConfig;
    use rand::Rng;

    use crate::circuit::array_to_bits;
    use crate::circuit::sha256;

    const EXPECTED_RES: [u8; 256] = [
        0, 0, 1, 1, 1, 1, 0, 0, 0, 1, 0, 1, 1, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1, 1, 1, 0, 1, 0,
        0, 1, 0, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 0,
        0, 0, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 1,
        0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0,
        1, 0, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 0, 0, 0, 0, 1, 1,
        1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1,
        1, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0,
        1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 0, 1, 1, 1, 0, 0, 0,
        0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0,
    ];

    #[test]
    fn test_sha256() -> Result<()> {
        let mut msg = vec![0; 128];
        for i in 0..127 {
            msg[i] = i as u8;
        }

        let msg_bits = array_to_bits(&msg);
        let len = msg.len() * 8;
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

        let message: Vec<BoolTarget> = (0..len)
            .map(|_| builder.add_virtual_bool_target_unsafe())
            .collect();
        let digest = sha256(&mut builder, message.as_slice());
        let mut pw = PartialWitness::new();

        for i in 0..len {
            pw.set_bool_target(message[i], msg_bits[i])?;
        }

        for i in 0..EXPECTED_RES.len() {
            if EXPECTED_RES[i] == 1 {
                builder.assert_one(digest[i].target);
            } else {
                builder.assert_zero(digest[i].target);
            }
        }

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof)
    }

    #[test]
    #[should_panic]
    fn test_sha256_failure() {
        let mut msg = vec![0; 128];
        for i in 0..127 {
            msg[i] = i as u8;
        }

        let msg_bits = array_to_bits(&msg);
        let len = msg.len() * 8;
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());
        let message: Vec<BoolTarget> = (0..len)
            .map(|_| builder.add_virtual_bool_target_unsafe())
            .collect();

        let digest = sha256(&mut builder, message.as_slice());
        let mut pw = PartialWitness::new();

        for i in 0..len {
            pw.set_bool_target(message[i], msg_bits[i]).unwrap();
        }

        let mut rng = rand::thread_rng();
        let rnd = rng.gen_range(0..256);
        for i in 0..EXPECTED_RES.len() {
            let b = (i == rnd && EXPECTED_RES[i] != 1) || (i != rnd && EXPECTED_RES[i] == 1);
            if b {
                builder.assert_one(digest[i].target);
            } else {
                builder.assert_zero(digest[i].target);
            }
        }

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof).expect("");
    }
}
