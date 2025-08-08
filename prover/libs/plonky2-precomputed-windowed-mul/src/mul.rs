use std::ops::Add;
use std::ops::Neg;

use k256::EncodedPoint;
use k256::ProjectivePoint;
use k256::elliptic_curve::BatchNormalize;
use k256::elliptic_curve::sec1::FromEncodedPoint;
use plonky2::field::extension::Extendable;
use plonky2::field::secp256k1_scalar::Secp256K1Scalar;
use plonky2::field::types::Field;
use plonky2::field::types::PrimeField64;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::Target;
use plonky2::iop::witness::Witness;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_ecdsa::curve::curve_types::AffinePoint;
use plonky2_ecdsa::curve::curve_types::Curve;
use plonky2_ecdsa::curve::secp256k1::Secp256K1;
use plonky2_ecdsa::gadgets::curve::AffinePointTarget;
use plonky2_ecdsa::gadgets::curve::CircuitBuilderCurve;
use plonky2_ecdsa::gadgets::curve::WitnessPoint;
use plonky2_ecdsa::gadgets::curve_windowed_mul::CircuitBuilderWindowedMul;
use plonky2_ecdsa::gadgets::nonnative::NonNativeTarget;
use plonky2_ecdsa::gadgets::split_nonnative::CircuitBuilderSplit;

use crate::utils::encoded_point_from_compressed_public_key;
use crate::utils::k256_affine_point_to_plonky2_affine_point;
use crate::utils::plonky2_affine_point_to_k256_affine_point;

/// A target storing the precomputation to perform windowed scalar multiplication.
#[derive(Clone)]
pub struct PrecomputedWindowedMulTarget {
    // the format for the precomputation is explained below in the doc for precompute_mul_table
    precomputation: Vec<Vec<AffinePointTarget<Secp256K1>>>,
}

/// If we perform a scalar multiplication and the curve point is known in advance (it is constant or a public input),
/// we can perform windowed scalar multiplication with the precomputation occurring outside the circuit.
/// PrecomputedWindowedMul provides API to do this.
pub trait CircuitBuilderPrecomputedWindowedMul {
    /// Add a virtual PrecomputedWindowedMulTarget.
    fn add_virtual_precomputed_windowed_mul_target(&mut self) -> PrecomputedWindowedMulTarget;

    /// Add a const PrecomputedWindowedMulTarget for a given point on Secp256K1.
    fn add_const_precomputed_windowed_mul_target(
        &mut self,
        point: AffinePoint<Secp256K1>,
    ) -> PrecomputedWindowedMulTarget;

    /// Add a constant PrecomputedWindowedMulTarget for basepoint scalar multiplication.
    fn add_basepoint_precomputed_windowed_mul_target(&mut self) -> PrecomputedWindowedMulTarget;

    /// Compute a scalar multiplication using the precomputation.
    fn scalar_mul(
        &mut self,
        table: &PrecomputedWindowedMulTarget,
        value: &NonNativeTarget<Secp256K1Scalar>,
    ) -> AffinePointTarget<Secp256K1>;

    /// Get the underlying point to this scalar multiplication precomputation,
    /// as an AffinePointTarget.
    fn get_affine_point(
        &mut self,
        table: &PrecomputedWindowedMulTarget,
    ) -> AffinePointTarget<Secp256K1>;

    /// Register a PrecomputedWindowedMulTarget as public.
    fn register_public_precomputed_windowed_mul(&mut self, table: &PrecomputedWindowedMulTarget);
}

pub trait WitnessPrecomputedWindowedMul<F: PrimeField64>: Witness<F> {
    /// Set the value for a PrecomputedWindowedMulTarget in the witness.
    fn set_precomputed_windowed_mul(
        &mut self,
        mul_table: &PrecomputedWindowedMulTarget,
        public_key: &AffinePoint<Secp256K1>,
    );
}

// We split a scalar (which is 256 bits long) into LIMBS groups of WINDOW_SIZE_BITS, in other words
// we write the scalar as a base WIDTH number which has LIMBS digits.
const WINDOW_SIZE_BITS: usize = 4;
const LIMBS: usize = 256 / WINDOW_SIZE_BITS;
const WIDTH: usize = 1 << WINDOW_SIZE_BITS;

/// The precomputation for a point P consists of computing j * WIDTH^i * P,
/// for 0 <= j < WIDTH and 0 <= i < LIMBS.
/// We also add a fixed "nothing up my sleeve" point to each entry to avoid dealing with zero (the
/// point at infinity).
pub fn precompute_mul_table(point: AffinePoint<Secp256K1>) -> Vec<Vec<AffinePoint<Secp256K1>>> {
    let point = plonky2_affine_point_to_k256_affine_point(point);

    let rand = k256::ProjectivePoint::from(get_rand());
    let point_projective: ProjectivePoint = k256::ProjectivePoint::from(point);

    let mut table_proj = vec![vec![ProjectivePoint::IDENTITY; WIDTH]; LIMBS];

    for i in 0..LIMBS {
        if i == 0 {
            table_proj[i][1] = point_projective;
        } else {
            table_proj[i][1] = table_proj[i - 1][WIDTH / 2].double();
        }
        for j in 2..WIDTH {
            if j % 2 == 0 {
                table_proj[i][j] = table_proj[i][j / 2].double();
            } else {
                table_proj[i][j] = table_proj[i][j - 1].add(table_proj[i][1]);
            }
        }
    }

    for row in table_proj.iter_mut() {
        for element in row.iter_mut() {
            *element = element.add(rand);
        }
    }

    let flattened_table: [ProjectivePoint; LIMBS * WIDTH] = table_proj
        .into_iter()
        .flatten()
        .collect::<Vec<ProjectivePoint>>()
        .try_into()
        .unwrap();

    let table: Vec<Vec<k256::AffinePoint>> =
        k256::ProjectivePoint::batch_normalize(&flattened_table)
            .chunks(WIDTH)
            .map(|chunk| chunk.to_vec())
            .collect();

    table
        .into_iter()
        .map(|inner| {
            inner
                .into_iter()
                .map(k256_affine_point_to_plonky2_affine_point)
                .collect()
        })
        .collect()
}

// a nothing up my sleeve point that is added to each entry in the precomputation table
fn get_rand() -> k256::AffinePoint {
    let encoded_point: EncodedPoint = encoded_point_from_compressed_public_key(
        [
            vec![0x02],
            "nothing up my sleeve nothing up ".as_bytes().to_vec(),
        ]
        .concat()
        .try_into()
        .unwrap(),
    )
    .unwrap();

    k256::AffinePoint::from_encoded_point(&encoded_point).unwrap()
}

// another nothing up my sleeve point to be used as the starting value for the sum when computing
// the scalar multiplication, which is to be subtracted at the end.
fn get_another_rand() -> k256::AffinePoint {
    // a different nothing up my sleeve point
    let encoded_point: EncodedPoint = encoded_point_from_compressed_public_key(
        [
            vec![0x03],
            "nothingupmysleeve.nothingupmysle".as_bytes().to_vec(),
        ]
        .concat()
        .try_into()
        .unwrap(),
    )
    .unwrap();

    k256::AffinePoint::from_encoded_point(&encoded_point).unwrap()
}

impl<F: RichField + Extendable<D>, const D: usize> CircuitBuilderPrecomputedWindowedMul
    for CircuitBuilder<F, D>
{
    fn add_virtual_precomputed_windowed_mul_target(&mut self) -> PrecomputedWindowedMulTarget {
        // We can safely use the unsafe version of add_virtual_affine_point_target here because
        // a virtual PrecomputedWindowedMulTarget can only be used as a public input. Thus the
        // verifier guarantees that the values for the PrecomputedWindowedMulTarget are valid.
        PrecomputedWindowedMulTarget {
            precomputation: (0..LIMBS)
                .map(|_| {
                    (0..WIDTH)
                        .map(|_| self.add_virtual_affine_point_target_unsafe())
                        .collect()
                })
                .collect(),
        }
    }

    fn add_const_precomputed_windowed_mul_target(
        &mut self,
        point: AffinePoint<Secp256K1>,
    ) -> PrecomputedWindowedMulTarget {
        PrecomputedWindowedMulTarget {
            precomputation: precompute_mul_table(point)
                .into_iter()
                .map(|v| {
                    v.into_iter()
                        .map(|point| self.constant_affine_point(point))
                        .collect()
                })
                .collect(),
        }
    }
    fn add_basepoint_precomputed_windowed_mul_target(&mut self) -> PrecomputedWindowedMulTarget {
        self.add_const_precomputed_windowed_mul_target(Secp256K1::GENERATOR_AFFINE)
    }

    fn scalar_mul(
        &mut self,
        table: &PrecomputedWindowedMulTarget,
        value: &NonNativeTarget<Secp256K1Scalar>,
    ) -> AffinePointTarget<Secp256K1> {
        let value_as_4bits_limbs = self.split_nonnative_to_4_bit_limbs(value);
        let value_as_4bits_limbs_padded = pad_4bits_limbs_to_256bits(self, value_as_4bits_limbs);

        let addends: Vec<_> = value_as_4bits_limbs_padded
            .into_iter()
            .enumerate()
            .map(|(i, k)| self.random_access_curve_points(k, table.precomputation[i].clone()))
            .collect();

        let rand = k256_affine_point_to_plonky2_affine_point(get_another_rand());
        let mut sum = self.constant_affine_point(rand);
        let neg_rand = self.constant_affine_point(rand.neg());
        for addend in addends {
            sum = self.curve_add(&sum, &addend);
        }
        sum = self.curve_add(&sum, &neg_rand);

        let correction = compute_correction(self);
        sum = self.curve_add(&sum, &correction);

        sum
    }

    fn get_affine_point(
        &mut self,
        table: &PrecomputedWindowedMulTarget,
    ) -> AffinePointTarget<Secp256K1> {
        let neg_rand =
            self.constant_affine_point(k256_affine_point_to_plonky2_affine_point(get_rand().neg()));
        self.curve_add(&table.precomputation[0][1].clone(), &neg_rand)
    }

    fn register_public_precomputed_windowed_mul(&mut self, table: &PrecomputedWindowedMulTarget) {
        for i in 0..LIMBS {
            for j in 0..WIDTH {
                self.register_public_affine_point(&table.precomputation[i][j]);
            }
        }
    }
}

// compute a constant affine point to correct the addition of the "nothing up my sleeve" point rand
// in each entry of the table. The correction is given by - LIMBS * rand.
fn compute_correction<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
) -> AffinePointTarget<Secp256K1> {
    let rand = k256_affine_point_to_plonky2_affine_point(get_rand());
    let neg_limbs = -Secp256K1Scalar::from_noncanonical_u128(LIMBS as u128);
    let correction = Secp256K1::convert(neg_limbs) * rand.to_projective();

    builder.constant_affine_point(correction.to_affine())
}
fn pad_4bits_limbs_to_256bits<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    mut value_as_4bits_limbs: Vec<Target>,
) -> Vec<Target> {
    let u256_as_4_bits_limbs_count = 64;
    (0..u256_as_4_bits_limbs_count - value_as_4bits_limbs.len())
        .for_each(|_| value_as_4bits_limbs.push(builder.zero()));

    value_as_4bits_limbs
}

impl<T: Witness<F>, F: PrimeField64> WitnessPrecomputedWindowedMul<F> for T {
    fn set_precomputed_windowed_mul(
        &mut self,
        mul_table: &PrecomputedWindowedMulTarget,
        public_key: &AffinePoint<Secp256K1>,
    ) {
        let table_data = precompute_mul_table(*public_key);
        for (mul_row, data_row) in mul_table.precomputation.iter().zip(table_data) {
            for (mul_element, data_element) in mul_row.iter().zip(data_row) {
                self.set_affine_point_target(mul_element, &data_element);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use num::BigUint;
    use num::Num;
    use plonky2::field::goldilocks_field::GoldilocksField;
    use plonky2::field::secp256k1_base::Secp256K1Base;
    use plonky2::field::secp256k1_scalar::Secp256K1Scalar;
    use plonky2::field::types::Field;
    use plonky2::iop::witness::PartialWitness;
    use plonky2::plonk::circuit_builder::CircuitBuilder;
    use plonky2::plonk::circuit_data::CircuitConfig;
    use plonky2::plonk::config::GenericConfig;
    use plonky2::plonk::config::KeccakGoldilocksConfig;
    use plonky2_ecdsa::curve::curve_types::AffinePoint;
    use plonky2_ecdsa::curve::curve_types::Curve;
    use plonky2_ecdsa::curve::secp256k1::Secp256K1;
    use plonky2_ecdsa::gadgets::curve::CircuitBuilderCurve;
    use plonky2_ecdsa::gadgets::curve::WitnessPoint;
    use plonky2_ecdsa::gadgets::nonnative::CircuitBuilderNonNative;
    use plonky2_ecdsa::gadgets::nonnative::WitnessNonNative;

    use super::CircuitBuilderPrecomputedWindowedMul;
    use super::LIMBS;
    use super::WIDTH;
    use super::precompute_mul_table;

    type C = KeccakGoldilocksConfig;
    type F = <C as GenericConfig<2>>::F;

    fn new_test_circuit_builder() -> CircuitBuilder<GoldilocksField, 2> {
        let config = CircuitConfig::wide_ecc_config();
        CircuitBuilder::<F, 2>::new(config)
    }

    fn test_circuit_generator_multiplication_with_tables_and_constants(
        scalar_field_element: Secp256K1Scalar,
        expected_multiplication_result_x: Secp256K1Base,
        expected_multiplication_result_y: Secp256K1Base,
    ) {
        let mut builder = new_test_circuit_builder();
        let table = builder.add_basepoint_precomputed_windowed_mul_target();
        let scalar_field_element_target = builder.constant_nonnative(scalar_field_element);
        let point = AffinePoint::nonzero(
            expected_multiplication_result_x,
            expected_multiplication_result_y,
        );
        let expected_result = builder.constant_affine_point(point);
        let generated_point = builder.scalar_mul(&table, &scalar_field_element_target);

        builder.connect_affine_point(&generated_point, &expected_result);

        let pw = PartialWitness::<GoldilocksField>::new();
        let data = builder.build::<C>();
        let proof = data.prove(pw.clone()).unwrap();
        assert!(data.verify(proof).is_ok())
    }

    fn test_circuit_generator_multiplication_with_tables_and_inputs(
        scalar_field_element: Secp256K1Scalar,
        expected_multiplication_result_x: Secp256K1Base,
        expected_multiplication_result_y: Secp256K1Base,
    ) {
        let mut builder = new_test_circuit_builder();
        let table = builder.add_basepoint_precomputed_windowed_mul_target();

        let scalar_field_element_target = builder.add_virtual_nonnative_target();

        let point = AffinePoint::nonzero(
            expected_multiplication_result_x,
            expected_multiplication_result_y,
        );
        let expected_result = builder.constant_affine_point(point);
        let generated_point = builder.scalar_mul(&table, &scalar_field_element_target);

        builder.connect_affine_point(&generated_point, &expected_result);

        let mut pw = PartialWitness::<GoldilocksField>::new();
        pw.set_non_native_target(&scalar_field_element_target, &scalar_field_element);
        let data = builder.build::<C>();
        let proof = data.prove(pw.clone()).unwrap();
        assert!(data.verify(proof).is_ok())
    }

    fn test_circuit_for_point(
        scalar_field_element: Secp256K1Scalar,
        point: AffinePoint<Secp256K1>,
        expected_multiplication_result_x: Secp256K1Base,
        expected_multiplication_result_y: Secp256K1Base,
    ) {
        let mut builder = new_test_circuit_builder();

        // Circuit
        let scalar_field_element_target = builder.add_virtual_nonnative_target();
        let table = builder.add_virtual_precomputed_windowed_mul_target();
        let generated_point = builder.scalar_mul(&table, &scalar_field_element_target);
        let expected_point = AffinePoint::nonzero(
            expected_multiplication_result_x,
            expected_multiplication_result_y,
        );
        let expected_result = builder.constant_affine_point(expected_point);
        builder.connect_affine_point(&generated_point, &expected_result);

        //Witness
        let mut pw = PartialWitness::<GoldilocksField>::new();
        pw.set_non_native_target(&scalar_field_element_target, &scalar_field_element);

        let table_data = precompute_mul_table(point);

        assert_eq!(table.precomputation.len(), LIMBS);
        assert_eq!(table_data.len(), LIMBS);

        for (mul_row, data_row) in table.precomputation.iter().zip(table_data) {
            assert_eq!(mul_row.len(), WIDTH);
            assert_eq!(data_row.len(), WIDTH);
            for (mul_element, data_element) in mul_row.iter().zip(data_row) {
                pw.set_affine_point_target(mul_element, &data_element);
            }
        }

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();
        assert!(data.verify(proof).is_ok())
    }

    #[test]
    fn test_can_multiply_curve_point_with_tables_with_constants() {
        let parsed_scalar_field_element = "7730c781f7ae53";

        let parsed_expected_result_x =
            "933ec2d2b111b92737ec12f1c5d20f3233a0ad21cd8b36d0bca7a0cfa5cb8701";
        let parsed_expected_result_y =
            "96cbbfdd572f75ace44d0aa59fbab6326cb9f909385dcd066ea27affef5a488c";

        let scalar_field_element = Secp256K1Scalar::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_scalar_field_element, 16).unwrap(),
        );
        let expected_result_x = Secp256K1Base::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_expected_result_x, 16).unwrap(),
        );
        let expected_result_y = Secp256K1Base::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_expected_result_y, 16).unwrap(),
        );

        test_circuit_generator_multiplication_with_tables_and_constants(
            scalar_field_element,
            expected_result_x,
            expected_result_y,
        );
    }

    #[test]
    fn test_can_multiply_curve_point_with_tables() {
        let parsed_scalar_field_element = "7730c781f7ae53";

        let parsed_expected_result_x =
            "933ec2d2b111b92737ec12f1c5d20f3233a0ad21cd8b36d0bca7a0cfa5cb8701";
        let parsed_expected_result_y =
            "96cbbfdd572f75ace44d0aa59fbab6326cb9f909385dcd066ea27affef5a488c";

        let scalar_field_element = Secp256K1Scalar::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_scalar_field_element, 16).unwrap(),
        );
        let expected_result_x = Secp256K1Base::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_expected_result_x, 16).unwrap(),
        );
        let expected_result_y = Secp256K1Base::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_expected_result_y, 16).unwrap(),
        );

        test_circuit_generator_multiplication_with_tables_and_inputs(
            scalar_field_element,
            expected_result_x,
            expected_result_y,
        );
    }

    #[test]
    fn test_base_multiplication_as_public_input() {
        let parsed_scalar_field_element = "7730c781f7ae53";

        let parsed_expected_result_x =
            "933ec2d2b111b92737ec12f1c5d20f3233a0ad21cd8b36d0bca7a0cfa5cb8701";
        let parsed_expected_result_y =
            "96cbbfdd572f75ace44d0aa59fbab6326cb9f909385dcd066ea27affef5a488c";

        let scalar_field_element = Secp256K1Scalar::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_scalar_field_element, 16).unwrap(),
        );
        let expected_result_x = Secp256K1Base::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_expected_result_x, 16).unwrap(),
        );
        let expected_result_y = Secp256K1Base::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_expected_result_y, 16).unwrap(),
        );

        test_circuit_for_point(
            scalar_field_element,
            Secp256K1::GENERATOR_AFFINE,
            expected_result_x,
            expected_result_y,
        )
    }
}
