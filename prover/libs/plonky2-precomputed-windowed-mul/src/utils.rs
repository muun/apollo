use anyhow::anyhow;
use k256::EncodedPoint;
use k256::elliptic_curve::sec1::FromEncodedPoint;
use k256::elliptic_curve::sec1::ToEncodedPoint;
use plonky2::field::secp256k1_base::Secp256K1Base;
use plonky2::field::secp256k1_scalar::Secp256K1Scalar;
use plonky2_ecdsa::curve::curve_types::AffinePoint;
use plonky2_ecdsa::curve::secp256k1::Secp256K1;

// This file provides functions convert serialize points and scalars to the format in Plonky2's
// off-circuit implementation of Secp256k1.

pub fn from_compressed_private_key(compressed_private_key: [u8; 32]) -> Secp256K1Scalar {
    Secp256K1Scalar(
        be_u8_arr_to_le_u64_arr(&compressed_private_key)
            .try_into()
            .unwrap(),
    )
}

pub fn encoded_point_from_compressed_public_key(
    compressed_public_key: [u8; 33],
) -> anyhow::Result<EncodedPoint> {
    let encoded_point = EncodedPoint::from_bytes(compressed_public_key)?;

    let affine_point = k256::AffinePoint::from_encoded_point(&encoded_point);

    if affine_point.is_none().unwrap_u8() == 1u8 {
        return Err(anyhow!("Failed to obtain affine point"));
    }

    Ok(affine_point.unwrap().to_encoded_point(false))
}

pub fn from_compressed_public_key(
    compressed_public_key: [u8; 33],
) -> anyhow::Result<AffinePoint<Secp256K1>> {
    let encoded_point = encoded_point_from_compressed_public_key(compressed_public_key)?;
    Ok(k256_encoded_point_to_plonky2_affine_point(&encoded_point))
}

pub fn k256_encoded_point_to_plonky2_affine_point(
    encoded_point: &EncodedPoint,
) -> AffinePoint<Secp256K1> {
    AffinePoint::nonzero(
        Secp256K1Base(
            be_u8_arr_to_le_u64_arr(encoded_point.x().unwrap())
                .try_into()
                .unwrap(),
        ),
        Secp256K1Base(
            be_u8_arr_to_le_u64_arr(encoded_point.y().unwrap())
                .try_into()
                .unwrap(),
        ),
    )
}

pub fn from_uncompressed_public_key(
    uncompressed_public_key: [u8; 65],
) -> anyhow::Result<AffinePoint<Secp256K1>> {
    let encoded_point = EncodedPoint::from_bytes(uncompressed_public_key)?;

    Ok(k256_affine_point_to_plonky2_affine_point(
        k256::AffinePoint::from_encoded_point(&encoded_point).unwrap(),
    ))
}

pub fn k256_affine_point_to_plonky2_affine_point(
    point: k256::AffinePoint,
) -> AffinePoint<Secp256K1> {
    k256_encoded_point_to_plonky2_affine_point(&point.to_encoded_point(false))
}

pub fn plonky2_affine_point_to_k256_affine_point(
    point: AffinePoint<Secp256K1>,
) -> k256::AffinePoint {
    let x = le_u64_arr_to_be_u8_arr(&point.x.0);
    let y = le_u64_arr_to_be_u8_arr(&point.y.0);
    let encoded_point = EncodedPoint::from_affine_coordinates(&x.into(), &y.into(), false);
    k256::AffinePoint::from_encoded_point(&encoded_point).unwrap()
}

fn be_u8_arr_to_le_u64_arr(input: &[u8]) -> Vec<u64> {
    assert_eq!(0, input.len() % 8);
    (0..input.len() / 8)
        .map(|i| u64::from_be_bytes(input[8 * i..8 * (i + 1)].try_into().unwrap()))
        .rev() // invert endianness
        .collect()
}

fn le_u64_arr_to_be_u8_arr(input: &[u64]) -> [u8; 32] {
    input
        .iter()
        .rev()
        .flat_map(|x: &u64| u64::to_be_bytes(*x))
        .collect::<Vec<u8>>()
        .try_into()
        .unwrap()
}
