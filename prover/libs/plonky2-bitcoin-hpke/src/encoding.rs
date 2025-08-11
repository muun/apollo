use plonky2::field::extension::Extendable;
use plonky2::field::secp256k1_scalar::Secp256K1Scalar;
use plonky2::field::types::Field;
use plonky2::hash::hash_types::RichField;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_bytes::ByteTarget;
use plonky2_bytes::CircuitBuilderBytes;
use plonky2_ecdsa::curve::secp256k1::Secp256K1;
use plonky2_ecdsa::gadgets::biguint::BigUintTarget;
use plonky2_ecdsa::gadgets::curve::AffinePointTarget;
use plonky2_ecdsa::gadgets::nonnative::CircuitBuilderNonNative;
use plonky2_ecdsa::gadgets::nonnative::NonNativeTarget;
use plonky2_sha256::circuit::bits_to_u32_target;
use plonky2_sha256::circuit::u32_to_bits_target;
use plonky2_u32::gadgets::arithmetic_u32::U32Target;

pub type PublicKeyTarget = AffinePointTarget<Secp256K1>;
pub type SecretKeyTarget = NonNativeTarget<Secp256K1Scalar>;

const UNCOMPRESSED_POINT_TAG: u8 = 0x04;

fn u32_to_bytes_be<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    value: &U32Target,
) -> Vec<ByteTarget> {
    u32_to_bits_target::<_, D>(builder, value)
        .chunks(8)
        .map(|chunk| ByteTarget {
            bits: chunk.try_into().unwrap(),
        })
        .collect()
}

fn bytes_to_u32_be<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    bytes: &[ByteTarget],
) -> U32Target {
    bits_to_u32_target(builder, bytes.iter().flat_map(|byte| byte.bits).collect())
}

pub fn serialize_nonnative_target<F: RichField + Extendable<D>, const D: usize, FF: Field>(
    builder: &mut CircuitBuilder<F, D>,
    non_native_target: NonNativeTarget<FF>,
) -> Vec<ByteTarget> {
    non_native_target
        .value
        .limbs
        .iter()
        .rev()
        .flat_map(|limb| u32_to_bytes_be(builder, limb))
        .collect()
}

pub fn serialize_uncompressed_public_key<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    public_key: &PublicKeyTarget,
) -> Vec<ByteTarget> {
    let tag = builder.constant_byte(UNCOMPRESSED_POINT_TAG);
    let x = serialize_nonnative_target(builder, public_key.x.clone());
    let y = serialize_nonnative_target(builder, public_key.y.clone());
    [&[tag] as &[_], &x, &y].concat()
}

pub fn serialize_x_coord<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    public_key: PublicKeyTarget,
) -> Vec<ByteTarget> {
    serialize_nonnative_target(builder, public_key.x)
}

pub fn serialize_private_key<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    private_key: SecretKeyTarget,
) -> Vec<ByteTarget> {
    private_key
        .value
        .limbs
        .iter()
        .rev()
        .flat_map(|limb| u32_to_bytes_be(builder, limb))
        .collect()
}

pub fn parse_field_element<FF: Field, F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    serialized: &[ByteTarget],
) -> NonNativeTarget<FF> {
    let limbs: Vec<_> = serialized
        .chunks(4)
        .map(|chunk| bytes_to_u32_be(builder, chunk))
        .rev()
        .collect();
    builder.biguint_to_nonnative(&BigUintTarget { limbs })
}

pub fn parse_uncompressed_public_key<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    serialized: &[ByteTarget],
) -> PublicKeyTarget {
    let x = parse_field_element(builder, &serialized[1..33]);
    let y = parse_field_element(builder, &serialized[33..65]);
    AffinePointTarget { x, y }
}

pub fn parse_private_key<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    serialized: &[ByteTarget],
) -> SecretKeyTarget {
    parse_field_element(builder, serialized)
}
