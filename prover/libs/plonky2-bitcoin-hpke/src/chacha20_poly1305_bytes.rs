use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::BoolTarget;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_bytes::ByteTarget;

// TODO we intend to use bytes in the plonky2-chacha20-poly1305 crate instead of having these wrapper
//  also we should move, bits_to_bytes and bytes_to_bits to the plonky2-bytes crate if they are still
//  needed (and we should be using the same endianness convention everywhere if possible).

fn bytes_to_bits(bytes: &[ByteTarget]) -> Vec<BoolTarget> {
    bytes
        .iter()
        .flat_map(|x| x.bits.iter().rev().cloned())
        .collect()
}

fn bits_to_bytes(bits: &[BoolTarget]) -> Vec<ByteTarget> {
    bits.chunks_exact(8)
        .map(|bits| ByteTarget {
            bits: bits
                .iter()
                .cloned()
                .rev()
                .collect::<Vec<_>>()
                .try_into()
                .unwrap(),
        })
        .collect()
}

pub fn chacha20_poly1305_bytes<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    key: &[ByteTarget],
    nonce: &[ByteTarget],
    plaintext: &[ByteTarget],
    aad: &[ByteTarget],
) -> Vec<ByteTarget> {
    let (ciphertext, tag) = plonky2_chacha20_poly1305::chacha20_poly1305(
        builder,
        bytes_to_bits(key).try_into().unwrap(),
        bytes_to_bits(nonce).try_into().unwrap(),
        bytes_to_bits(plaintext),
        bytes_to_bits(aad),
    );
    bits_to_bytes(&[&ciphertext as &[BoolTarget], &tag].concat())
}
