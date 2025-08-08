use alloc::vec;
use alloc::vec::Vec;

use plonky2::field::extension::Extendable;
use plonky2::field::types::PrimeField64;
use plonky2::field::types::Sample;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::BoolTarget;
use plonky2::iop::witness::Witness;
use plonky2::plonk::circuit_builder::CircuitBuilder;

use crate::curve::curve_types::AffinePoint;
use crate::curve::curve_types::Curve;
use crate::curve::curve_types::CurveScalar;
use crate::curve::secp256k1::Secp256K1;
use crate::gadgets::nonnative::CircuitBuilderNonNative;
use crate::gadgets::nonnative::NonNativeTarget;
use crate::gadgets::nonnative::WitnessNonNative;

/// A Target representing an affine point on the curve `C`. We use incomplete arithmetic for efficiency,
/// so we assume these points are not zero.
#[derive(Clone, Debug)]
pub struct AffinePointTarget<C: Curve> {
    pub x: NonNativeTarget<C::BaseField>,
    pub y: NonNativeTarget<C::BaseField>,
}

impl<C: Curve> AffinePointTarget<C> {
    pub fn to_vec(&self) -> Vec<NonNativeTarget<C::BaseField>> {
        vec![self.x.clone(), self.y.clone()]
    }
}

pub trait CircuitBuilderCurve<F: RichField + Extendable<D>, const D: usize> {
    fn constant_affine_point<C: Curve>(&mut self, point: AffinePoint<C>) -> AffinePointTarget<C>;

    fn connect_affine_point<C: Curve>(
        &mut self,
        lhs: &AffinePointTarget<C>,
        rhs: &AffinePointTarget<C>,
    );

    /// Add a virtual AffinePointTarget, with all the range checks that ensure its coordinates are
    /// valid base field elements.
    fn add_virtual_affine_point_target<C: Curve>(&mut self) -> AffinePointTarget<C>;

    /// Add a virtual AffinePointTarget, without any range checks. The caller MUST be sure that each
    /// coordinate is a valid base field element. This means making sure the U32Target limbs of each
    /// of its coordinates are in the range [0,2^32) and that the BigUintTarget coordinates are in
    /// the range [0,base_field_order).
    fn add_virtual_affine_point_target_unsafe<C: Curve>(&mut self) -> AffinePointTarget<C>;

    fn curve_assert_valid<C: Curve>(&mut self, p: &AffinePointTarget<C>);

    fn curve_neg<C: Curve>(&mut self, p: &AffinePointTarget<C>) -> AffinePointTarget<C>;

    fn curve_conditional_neg<C: Curve>(
        &mut self,
        p: &AffinePointTarget<C>,
        b: BoolTarget,
    ) -> AffinePointTarget<C>;

    fn curve_double<C: Curve>(&mut self, p: &AffinePointTarget<C>) -> AffinePointTarget<C>;

    fn curve_repeated_double<C: Curve>(
        &mut self,
        p: &AffinePointTarget<C>,
        n: usize,
    ) -> AffinePointTarget<C>;

    /// Add two points, which are assumed to be non-equal.
    fn curve_add<C: Curve>(
        &mut self,
        p1: &AffinePointTarget<C>,
        p2: &AffinePointTarget<C>,
    ) -> AffinePointTarget<C>;

    fn curve_conditional_add<C: Curve>(
        &mut self,
        p1: &AffinePointTarget<C>,
        p2: &AffinePointTarget<C>,
        b: BoolTarget,
    ) -> AffinePointTarget<C>;

    fn curve_scalar_mul<C: Curve>(
        &mut self,
        p: &AffinePointTarget<C>,
        n: &NonNativeTarget<C::ScalarField>,
    ) -> AffinePointTarget<C>;

    fn register_public_affine_point<C: Curve>(&mut self, p: &AffinePointTarget<C>);
}

impl<F: RichField + Extendable<D>, const D: usize> CircuitBuilderCurve<F, D>
    for CircuitBuilder<F, D>
{
    fn constant_affine_point<C: Curve>(&mut self, point: AffinePoint<C>) -> AffinePointTarget<C> {
        debug_assert!(!point.zero);
        AffinePointTarget {
            x: self.constant_nonnative(point.x),
            y: self.constant_nonnative(point.y),
        }
    }

    fn connect_affine_point<C: Curve>(
        &mut self,
        lhs: &AffinePointTarget<C>,
        rhs: &AffinePointTarget<C>,
    ) {
        self.connect_nonnative(&lhs.x, &rhs.x);
        self.connect_nonnative(&lhs.y, &rhs.y);
    }

    fn add_virtual_affine_point_target<C: Curve>(&mut self) -> AffinePointTarget<C> {
        let x = self.add_virtual_nonnative_target();
        let y = self.add_virtual_nonnative_target();

        AffinePointTarget { x, y }
    }

    fn add_virtual_affine_point_target_unsafe<C: Curve>(&mut self) -> AffinePointTarget<C> {
        let x = self.add_virtual_nonnative_target_unsafe();
        let y = self.add_virtual_nonnative_target_unsafe();

        AffinePointTarget { x, y }
    }

    fn curve_assert_valid<C: Curve>(&mut self, p: &AffinePointTarget<C>) {
        let a = self.constant_nonnative(C::A);
        let b = self.constant_nonnative(C::B);

        let y_squared = self.mul_nonnative(&p.y, &p.y);
        let x_squared = self.mul_nonnative(&p.x, &p.x);
        let x_cubed = self.mul_nonnative(&x_squared, &p.x);
        let a_x = self.mul_nonnative(&a, &p.x);
        let a_x_plus_b = self.add_nonnative(&a_x, &b);
        let rhs = self.add_nonnative(&x_cubed, &a_x_plus_b);

        self.connect_nonnative(&y_squared, &rhs);
    }

    fn curve_neg<C: Curve>(&mut self, p: &AffinePointTarget<C>) -> AffinePointTarget<C> {
        let neg_y = self.neg_nonnative(&p.y);
        AffinePointTarget {
            x: p.x.clone(),
            y: neg_y,
        }
    }

    fn curve_conditional_neg<C: Curve>(
        &mut self,
        p: &AffinePointTarget<C>,
        b: BoolTarget,
    ) -> AffinePointTarget<C> {
        AffinePointTarget {
            x: p.x.clone(),
            y: self.nonnative_conditional_neg(&p.y, b),
        }
    }

    fn curve_double<C: Curve>(&mut self, p: &AffinePointTarget<C>) -> AffinePointTarget<C> {
        let AffinePointTarget { x, y } = p;
        let double_y = self.add_nonnative(y, y);
        let inv_double_y = self.inv_nonnative(&double_y);
        let x_squared = self.mul_nonnative(x, x);
        let double_x_squared = self.add_nonnative(&x_squared, &x_squared);
        let triple_x_squared = self.add_nonnative(&double_x_squared, &x_squared);

        let a = self.constant_nonnative(C::A);
        let triple_xx_a = self.add_nonnative(&triple_x_squared, &a);
        let lambda = self.mul_nonnative(&triple_xx_a, &inv_double_y);
        let lambda_squared = self.mul_nonnative(&lambda, &lambda);
        let x_double = self.add_nonnative(x, x);

        let x3 = self.sub_nonnative(&lambda_squared, &x_double);

        let x_diff = self.sub_nonnative(x, &x3);
        let lambda_x_diff = self.mul_nonnative(&lambda, &x_diff);

        let y3 = self.sub_nonnative(&lambda_x_diff, y);

        AffinePointTarget { x: x3, y: y3 }
    }

    fn curve_repeated_double<C: Curve>(
        &mut self,
        p: &AffinePointTarget<C>,
        n: usize,
    ) -> AffinePointTarget<C> {
        let mut result = p.clone();

        for _ in 0..n {
            result = self.curve_double(&result);
        }

        result
    }

    fn curve_add<C: Curve>(
        &mut self,
        p1: &AffinePointTarget<C>,
        p2: &AffinePointTarget<C>,
    ) -> AffinePointTarget<C> {
        let AffinePointTarget { x: x1, y: y1 } = p1;
        let AffinePointTarget { x: x2, y: y2 } = p2;

        let u = self.sub_nonnative(y2, y1);
        let v = self.sub_nonnative(x2, x1);
        let v_inv = self.inv_nonnative(&v);
        let s = self.mul_nonnative(&u, &v_inv);
        let s_squared = self.mul_nonnative(&s, &s);
        let x_sum = self.add_nonnative(x2, x1);
        let x3 = self.sub_nonnative(&s_squared, &x_sum);
        let x_diff = self.sub_nonnative(x1, &x3);
        let prod = self.mul_nonnative(&s, &x_diff);
        let y3 = self.sub_nonnative(&prod, y1);

        AffinePointTarget { x: x3, y: y3 }
    }

    fn curve_conditional_add<C: Curve>(
        &mut self,
        p1: &AffinePointTarget<C>,
        p2: &AffinePointTarget<C>,
        b: BoolTarget,
    ) -> AffinePointTarget<C> {
        let not_b = self.not(b);
        let sum = self.curve_add(p1, p2);
        let x_if_true = self.mul_nonnative_by_bool(&sum.x, b);
        let y_if_true = self.mul_nonnative_by_bool(&sum.y, b);
        let x_if_false = self.mul_nonnative_by_bool(&p1.x, not_b);
        let y_if_false = self.mul_nonnative_by_bool(&p1.y, not_b);

        let x = self.add_nonnative(&x_if_true, &x_if_false);
        let y = self.add_nonnative(&y_if_true, &y_if_false);

        AffinePointTarget { x, y }
    }

    fn curve_scalar_mul<C: Curve>(
        &mut self,
        p: &AffinePointTarget<C>,
        n: &NonNativeTarget<C::ScalarField>,
    ) -> AffinePointTarget<C> {
        let bits = self.split_nonnative_to_bits(n);

        let rando = (CurveScalar(C::ScalarField::rand()) * C::GENERATOR_PROJECTIVE).to_affine();
        let randot = self.constant_affine_point(rando);
        // Result starts at `rando`, which is later subtracted, because we don't support arithmetic with the zero point.
        let mut result = self.add_virtual_affine_point_target();
        self.connect_affine_point(&randot, &result);

        let mut two_i_times_p = self.add_virtual_affine_point_target();
        self.connect_affine_point(p, &two_i_times_p);

        for &bit in bits.iter() {
            let not_bit = self.not(bit);

            let result_plus_2_i_p = self.curve_add(&result, &two_i_times_p);

            let new_x_if_bit = self.mul_nonnative_by_bool(&result_plus_2_i_p.x, bit);
            let new_x_if_not_bit = self.mul_nonnative_by_bool(&result.x, not_bit);
            let new_y_if_bit = self.mul_nonnative_by_bool(&result_plus_2_i_p.y, bit);
            let new_y_if_not_bit = self.mul_nonnative_by_bool(&result.y, not_bit);

            let new_x = self.add_nonnative(&new_x_if_bit, &new_x_if_not_bit);
            let new_y = self.add_nonnative(&new_y_if_bit, &new_y_if_not_bit);

            result = AffinePointTarget { x: new_x, y: new_y };

            two_i_times_p = self.curve_double(&two_i_times_p);
        }

        // Subtract off result's intial value of `rando`.
        let neg_r = self.curve_neg(&randot);
        result = self.curve_add(&result, &neg_r);

        result
    }

    fn register_public_affine_point<C: Curve>(&mut self, p: &AffinePointTarget<C>) {
        self.register_public_nonnative(&p.x);
        self.register_public_nonnative(&p.y);
    }
}

pub trait WitnessPoint<F: PrimeField64>: Witness<F> {
    fn set_affine_point_target(
        &mut self,
        target: &AffinePointTarget<Secp256K1>,
        affine_point: &AffinePoint<Secp256K1>,
    );
}

impl<T: Witness<F>, F: PrimeField64> WitnessPoint<F> for T {
    fn set_affine_point_target(
        &mut self,
        target: &AffinePointTarget<Secp256K1>,
        affine_point: &AffinePoint<Secp256K1>,
    ) {
        self.set_non_native_target(&target.x, &affine_point.x);
        self.set_non_native_target(&target.y, &affine_point.y);
    }
}

#[cfg(test)]
mod tests {
    use core::ops::Neg;

    use anyhow::Result;
    use num::BigUint;
    use num::Num;
    use plonky2::field::goldilocks_field::GoldilocksField;
    use plonky2::field::secp256k1_base::Secp256K1Base;
    use plonky2::field::secp256k1_scalar::Secp256K1Scalar;
    use plonky2::field::types::Field;
    use plonky2::field::types::Sample;
    use plonky2::iop::witness::PartialWitness;
    use plonky2::plonk::circuit_builder::CircuitBuilder;
    use plonky2::plonk::circuit_data::CircuitConfig;
    use plonky2::plonk::config::GenericConfig;
    use plonky2::plonk::config::KeccakGoldilocksConfig;
    use plonky2::plonk::config::PoseidonGoldilocksConfig;

    use crate::curve::curve_types::AffinePoint;
    use crate::curve::curve_types::Curve;
    use crate::curve::curve_types::CurveScalar;
    use crate::curve::secp256k1::Secp256K1;
    use crate::gadgets::curve::CircuitBuilderCurve;
    use crate::gadgets::nonnative::CircuitBuilderNonNative;

    #[test]
    fn test_curve_point_is_valid() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;

        let config = CircuitConfig::standard_ecc_config();

        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let g = Secp256K1::GENERATOR_AFFINE;
        let g_target = builder.constant_affine_point(g);
        let neg_g_target = builder.curve_neg(&g_target);

        builder.curve_assert_valid(&g_target);
        builder.curve_assert_valid(&neg_g_target);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof)
    }

    #[test]
    #[should_panic]
    fn test_curve_point_is_not_valid() {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;

        let config = CircuitConfig::standard_ecc_config();

        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let g = Secp256K1::GENERATOR_AFFINE;
        let not_g = AffinePoint::<Secp256K1> {
            x: g.x,
            y: g.y + Secp256K1Base::ONE,
            zero: g.zero,
        };
        let not_g_target = builder.constant_affine_point(not_g);

        builder.curve_assert_valid(&not_g_target);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof).unwrap()
    }

    #[test]
    fn test_curve_double() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;

        let config = CircuitConfig::standard_ecc_config();

        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let g = Secp256K1::GENERATOR_AFFINE;
        let g_target = builder.constant_affine_point(g);
        let neg_g_target = builder.curve_neg(&g_target);

        let double_g = g.double();
        let double_g_expected = builder.constant_affine_point(double_g);
        builder.curve_assert_valid(&double_g_expected);

        let double_neg_g = (-g).double();
        let double_neg_g_expected = builder.constant_affine_point(double_neg_g);
        builder.curve_assert_valid(&double_neg_g_expected);

        let double_g_actual = builder.curve_double(&g_target);
        let double_neg_g_actual = builder.curve_double(&neg_g_target);
        builder.curve_assert_valid(&double_g_actual);
        builder.curve_assert_valid(&double_neg_g_actual);

        builder.connect_affine_point(&double_g_expected, &double_g_actual);
        builder.connect_affine_point(&double_neg_g_expected, &double_neg_g_actual);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof)
    }

    #[test]
    fn test_curve_add() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;

        let config = CircuitConfig::standard_ecc_config();

        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let g = Secp256K1::GENERATOR_AFFINE;
        let double_g = g.double();
        let g_plus_2g = (g + double_g).to_affine();
        let g_plus_2g_expected = builder.constant_affine_point(g_plus_2g);
        builder.curve_assert_valid(&g_plus_2g_expected);

        let g_target = builder.constant_affine_point(g);
        let double_g_target = builder.curve_double(&g_target);
        let g_plus_2g_actual = builder.curve_add(&g_target, &double_g_target);
        builder.curve_assert_valid(&g_plus_2g_actual);

        builder.connect_affine_point(&g_plus_2g_expected, &g_plus_2g_actual);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof)
    }

    #[test]
    fn test_curve_conditional_add() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;

        let config = CircuitConfig::standard_ecc_config();

        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let g = Secp256K1::GENERATOR_AFFINE;
        let double_g = g.double();
        let g_plus_2g = (g + double_g).to_affine();
        let g_plus_2g_expected = builder.constant_affine_point(g_plus_2g);

        let g_expected = builder.constant_affine_point(g);
        let double_g_target = builder.curve_double(&g_expected);
        let t = builder._true();
        let f = builder._false();
        let g_plus_2g_actual = builder.curve_conditional_add(&g_expected, &double_g_target, t);
        let g_actual = builder.curve_conditional_add(&g_expected, &double_g_target, f);

        builder.connect_affine_point(&g_plus_2g_expected, &g_plus_2g_actual);
        builder.connect_affine_point(&g_expected, &g_actual);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof)
    }

    #[test]
    #[ignore]
    fn test_curve_mul() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;

        let config = CircuitConfig::standard_ecc_config();

        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let g = Secp256K1::GENERATOR_PROJECTIVE.to_affine();
        let five = Secp256K1Scalar::from_canonical_usize(5);
        let neg_five = five.neg();
        let neg_five_scalar = CurveScalar::<Secp256K1>(neg_five);
        let neg_five_g = (neg_five_scalar * g.to_projective()).to_affine();
        let neg_five_g_expected = builder.constant_affine_point(neg_five_g);
        builder.curve_assert_valid(&neg_five_g_expected);

        let g_target = builder.constant_affine_point(g);
        let neg_five_target = builder.constant_nonnative(neg_five);
        let neg_five_g_actual = builder.curve_scalar_mul(&g_target, &neg_five_target);
        builder.curve_assert_valid(&neg_five_g_actual);

        builder.connect_affine_point(&neg_five_g_expected, &neg_five_g_actual);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof)
    }

    #[test]
    #[ignore]
    fn test_curve_random() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;

        let config = CircuitConfig::standard_ecc_config();

        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let rando =
            (CurveScalar(Secp256K1Scalar::rand()) * Secp256K1::GENERATOR_PROJECTIVE).to_affine();
        let randot = builder.constant_affine_point(rando);

        let two_target = builder.constant_nonnative(Secp256K1Scalar::TWO);
        let randot_doubled = builder.curve_double(&randot);
        let randot_times_two = builder.curve_scalar_mul(&randot, &two_target);
        builder.connect_affine_point(&randot_doubled, &randot_times_two);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();

        data.verify(proof)
    }

    type C = KeccakGoldilocksConfig;
    type F = <C as GenericConfig<2>>::F;

    fn new_test_circuit_builder() -> CircuitBuilder<GoldilocksField, 2> {
        let config = CircuitConfig::wide_ecc_config();
        CircuitBuilder::<F, 2>::new(config)
    }

    fn test_circuit_generator_add_inverse(point_p_x: Secp256K1Base, point_p_y: Secp256K1Base) {
        let mut builder = new_test_circuit_builder();

        let point = AffinePoint::<Secp256K1> {
            x: point_p_x,
            y: point_p_y,
            zero: false,
        };

        let curve_point = builder.constant_affine_point(point);

        let neg_curve_point = builder.curve_neg(&curve_point);
        let point_infinite = builder.curve_add(&curve_point, &neg_curve_point);

        let g_target = builder.constant_affine_point(Secp256K1::GENERATOR_AFFINE);
        let new_result = builder.curve_add(&point_infinite, &g_target);
        builder.connect_affine_point(&g_target, &new_result);
        //builder.connect_affine_point(&generated_point, &curve_point);

        let pw = PartialWitness::<GoldilocksField>::new();
        let data = builder.build::<C>();
        let proof = data.prove(pw.clone()).unwrap();
        assert!(data.verify(proof).is_ok())
    }

    #[test]
    #[ignore = "fails"]
    fn test_can_multiply_curve_point_with_tables() {
        let parsed_curve_point_x =
            "933ec2d2b111b92737ec12f1c5d20f3233a0ad21cd8b36d0bca7a0cfa5cb8701";
        let parsed_curve_point_y =
            "96cbbfdd572f75ace44d0aa59fbab6326cb9f909385dcd066ea27affef5a488c";
        let curve_point_x = Secp256K1Base::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_curve_point_x, 16).unwrap(),
        );
        let curve_point_y = Secp256K1Base::from_noncanonical_biguint(
            BigUint::from_str_radix(parsed_curve_point_y, 16).unwrap(),
        );

        test_circuit_generator_add_inverse(curve_point_x, curve_point_y)
    }
}
