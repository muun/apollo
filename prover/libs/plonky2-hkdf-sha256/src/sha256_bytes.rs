use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::BoolTarget;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_bytes::ByteTarget;
use plonky2_bytes::bytes_from_bits;
use plonky2_sha256::circuit::sha256;

pub const DIGEST_SIZE_IN_BYTES: usize = 32;

pub type DigestTarget = [ByteTarget; DIGEST_SIZE_IN_BYTES];

pub fn sha256_bytes<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    message_bytes: &[ByteTarget],
) -> DigestTarget {
    let message_bits: Vec<BoolTarget> = message_bytes.iter().flat_map(|x| x.bits).collect();
    let digest = sha256(builder, message_bits.as_slice());

    bytes_from_bits(digest.as_slice()).try_into().unwrap()
}
