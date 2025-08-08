use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_bytes::ByteTarget;
use plonky2_precomputed_windowed_mul::CircuitBuilderPrecomputedWindowedMul;
use plonky2_precomputed_windowed_mul::PrecomputedWindowedMulTarget;

use crate::constants::LENGTH_DIFFIE_HELLMAN_BYTES;
use crate::constants::LENGTH_ENCAPSULATED_BYTES;
use crate::constants::LENGTH_PUBLIC_KEY_BYTES;
use crate::constants::LENGTH_SECRET_BYTES;
use crate::encoding::PublicKeyTarget;
use crate::encoding::SecretKeyTarget;
use crate::encoding::serialize_uncompressed_public_key;
use crate::encoding::serialize_x_coord;
use crate::labeled_hkdf::Usage;
use crate::labeled_hkdf::labeled_expand;
use crate::labeled_hkdf::labeled_extract;

fn get_public_key<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    private_key: &SecretKeyTarget,
) -> PublicKeyTarget {
    let generator = builder.add_basepoint_precomputed_windowed_mul_target();
    builder.scalar_mul(&generator, private_key)
}

fn diffie_hellman<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    private_key: &SecretKeyTarget,
    public_key: &PrecomputedWindowedMulTarget,
) -> Vec<ByteTarget> {
    let shared_secret = builder.scalar_mul(public_key, private_key);
    serialize_x_coord(builder, shared_secret)
}

fn extract_and_expand<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    diffie_hellman_shared_secret: &[ByteTarget],
    kem_context: &[ByteTarget],
) -> Vec<ByteTarget> {
    let eae_prk = labeled_extract(
        builder,
        &[],
        b"eae_prk",
        diffie_hellman_shared_secret,
        Usage::Kem,
    );
    labeled_expand(
        builder,
        &eae_prk,
        b"shared_secret",
        kem_context,
        LENGTH_SECRET_BYTES,
        Usage::Kem,
    )
}

pub fn encap<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    ephemeral_private_key: &SecretKeyTarget,
    receiver_public_key: &PrecomputedWindowedMulTarget,
) -> (Vec<ByteTarget>, Vec<ByteTarget>) {
    let diffie_hellman_shared_secret =
        diffie_hellman(builder, ephemeral_private_key, receiver_public_key);
    assert_eq!(
        diffie_hellman_shared_secret.len(),
        LENGTH_DIFFIE_HELLMAN_BYTES
    );
    let ephemeral_public_key = get_public_key(builder, ephemeral_private_key);
    let enc = serialize_uncompressed_public_key(builder, &ephemeral_public_key);
    assert_eq!(enc.len(), LENGTH_PUBLIC_KEY_BYTES);
    let receiver_public_key = builder.get_affine_point(receiver_public_key);
    let kem_context = [
        &enc as &[_],
        &serialize_uncompressed_public_key(builder, &receiver_public_key),
    ]
    .concat();
    let shared_secret = extract_and_expand(builder, &diffie_hellman_shared_secret, &kem_context);
    assert_eq!(enc.len(), LENGTH_ENCAPSULATED_BYTES);
    (shared_secret, enc)
}
