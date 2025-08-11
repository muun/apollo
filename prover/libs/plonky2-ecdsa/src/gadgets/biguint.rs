use alloc::string::String;
use alloc::vec;
use alloc::vec::Vec;
use core::marker::PhantomData;

use num::BigUint;
use num::Integer;
use num::Zero;
use plonky2::field::extension::Extendable;
use plonky2::field::types::PrimeField;
use plonky2::field::types::PrimeField64;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::generator::GeneratedValues;
use plonky2::iop::generator::SimpleGenerator;
use plonky2::iop::target::BoolTarget;
use plonky2::iop::target::Target;
use plonky2::iop::witness::PartitionWitness;
use plonky2::iop::witness::Witness;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2::plonk::circuit_data::CommonCircuitData;
use plonky2::util::serialization::Buffer;
use plonky2::util::serialization::IoResult;
use plonky2_u32::gadgets::arithmetic_u32::CircuitBuilderU32;
use plonky2_u32::gadgets::arithmetic_u32::U32Target;
use plonky2_u32::gadgets::multiple_comparison::list_le_u32_circuit;
use plonky2_u32::witness::GeneratedValuesU32;
use plonky2_u32::witness::WitnessU32;

#[derive(Clone, Debug)]
pub struct BigUintTarget {
    pub limbs: Vec<U32Target>,
}

impl BigUintTarget {
    pub fn num_limbs(&self) -> usize {
        self.limbs.len()
    }

    pub fn get_limb(&self, i: usize) -> U32Target {
        self.limbs[i]
    }
}

pub trait CircuitBuilderBiguint<F: RichField + Extendable<D>, const D: usize> {
    fn constant_biguint(&mut self, value: &BigUint) -> BigUintTarget;

    fn zero_biguint(&mut self) -> BigUintTarget;

    fn connect_biguint(&mut self, lhs: &BigUintTarget, rhs: &BigUintTarget);

    fn pad_biguints(
        &mut self,
        a: &BigUintTarget,
        b: &BigUintTarget,
    ) -> (BigUintTarget, BigUintTarget);

    fn cmp_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BoolTarget;

    /// Add a virtual BigUintTarget with num_limbs limbs.
    fn add_virtual_biguint_target(&mut self, num_limbs: usize) -> BigUintTarget;

    /// Add a virtual BigUintTarget with num_limbs limbs. The caller must be sure that the U32Target
    /// limbs are constrained to the range [0,2^32) because the method sets no range checks to this end.
    fn add_virtual_biguint_target_unsafe(&mut self, num_limbs: usize) -> BigUintTarget;

    /// Add two `BigUintTarget`s.
    fn add_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget;

    /// Subtract two `BigUintTarget`s. We assume that the first is larger than the second.
    fn sub_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget;

    fn mul_biguint_u32(&mut self, a: &BigUintTarget, b: U32Target) -> BigUintTarget;

    fn mul_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget;

    fn mul_biguint_by_bool(&mut self, a: &BigUintTarget, b: BoolTarget) -> BigUintTarget;

    /// Returns x * y + z. This is no more efficient than mul-then-add; it's purely for convenience (only need to call one CircuitBuilder function).
    fn mul_add_biguint(
        &mut self,
        x: &BigUintTarget,
        y: &BigUintTarget,
        z: &BigUintTarget,
    ) -> BigUintTarget;

    fn div_rem_biguint(
        &mut self,
        a: &BigUintTarget,
        b: &BigUintTarget,
    ) -> (BigUintTarget, BigUintTarget);

    fn div_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget;

    fn rem_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget;

    fn range_check_biguint(&mut self, value: &BigUintTarget, order: &BigUintTarget);
}

impl<F: RichField + Extendable<D>, const D: usize> CircuitBuilderBiguint<F, D>
    for CircuitBuilder<F, D>
{
    fn constant_biguint(&mut self, value: &BigUint) -> BigUintTarget {
        let limb_values = value.to_u32_digits();
        let limbs = limb_values.iter().map(|&l| self.constant_u32(l)).collect();

        BigUintTarget { limbs }
    }

    fn zero_biguint(&mut self) -> BigUintTarget {
        self.constant_biguint(&BigUint::zero())
    }

    fn connect_biguint(&mut self, lhs: &BigUintTarget, rhs: &BigUintTarget) {
        let min_limbs = lhs.num_limbs().min(rhs.num_limbs());
        for i in 0..min_limbs {
            self.connect_u32(lhs.get_limb(i), rhs.get_limb(i));
        }

        for i in min_limbs..lhs.num_limbs() {
            self.assert_zero_u32(lhs.get_limb(i));
        }
        for i in min_limbs..rhs.num_limbs() {
            self.assert_zero_u32(rhs.get_limb(i));
        }
    }

    fn pad_biguints(
        &mut self,
        a: &BigUintTarget,
        b: &BigUintTarget,
    ) -> (BigUintTarget, BigUintTarget) {
        if a.num_limbs() > b.num_limbs() {
            let mut padded_b = b.clone();
            for _ in b.num_limbs()..a.num_limbs() {
                padded_b.limbs.push(self.zero_u32());
            }

            (a.clone(), padded_b)
        } else {
            let mut padded_a = a.clone();
            for _ in a.num_limbs()..b.num_limbs() {
                padded_a.limbs.push(self.zero_u32());
            }

            (padded_a, b.clone())
        }
    }

    // Returns true if a <= b
    fn cmp_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BoolTarget {
        let (a, b) = self.pad_biguints(a, b);

        list_le_u32_circuit(self, a.limbs, b.limbs)
    }

    fn add_virtual_biguint_target(&mut self, num_limbs: usize) -> BigUintTarget {
        let limbs = self.add_virtual_u32_targets(num_limbs);

        BigUintTarget { limbs }
    }

    fn add_virtual_biguint_target_unsafe(&mut self, num_limbs: usize) -> BigUintTarget {
        let limbs = self.add_virtual_u32_targets_unsafe(num_limbs);

        BigUintTarget { limbs }
    }

    fn add_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget {
        let num_limbs = a.num_limbs().max(b.num_limbs());

        let mut combined_limbs = vec![];
        let mut carry = self.zero_u32();
        for i in 0..num_limbs {
            let a_limb = (i < a.num_limbs())
                .then(|| a.limbs[i])
                .unwrap_or_else(|| self.zero_u32());
            let b_limb = (i < b.num_limbs())
                .then(|| b.limbs[i])
                .unwrap_or_else(|| self.zero_u32());

            let (new_limb, new_carry) = self.add_u32s_with_carry(&[a_limb, b_limb], carry);
            carry = new_carry;
            combined_limbs.push(new_limb);
        }
        combined_limbs.push(carry);

        BigUintTarget {
            limbs: combined_limbs,
        }
    }

    fn sub_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget {
        let (a, b) = self.pad_biguints(a, b);
        let num_limbs = a.limbs.len();

        let mut result_limbs = vec![];

        let mut borrow = self.zero_u32();
        for i in 0..num_limbs {
            let (result, new_borrow) = self.sub_u32(a.limbs[i], b.limbs[i], borrow);
            result_limbs.push(result);
            borrow = new_borrow;
        }
        // Borrow should be zero here.

        BigUintTarget {
            limbs: result_limbs,
        }
    }

    fn mul_biguint_u32(&mut self, a: &BigUintTarget, b: U32Target) -> BigUintTarget {
        let mut res = vec![];
        let mut carry = self.zero_u32();
        for ai in &a.limbs {
            let (lo, hi) = self.mul_add_u32(*ai, b, carry);
            carry = hi;
            res.push(lo);
        }
        res.push(carry);

        BigUintTarget { limbs: res }
    }

    fn mul_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget {
        // set a, b such that b.limbs.len() <= a.limbs.len()
        let (a, b) = if b.limbs.len() <= a.limbs.len() {
            (a, b)
        } else {
            (b, a)
        };
        if b.limbs.is_empty() {
            // TODO: debug why this is happening in our pairing code...
            return self.zero_biguint();
        }
        if b.limbs.len() == 1 {
            return self.mul_biguint_u32(a, b.limbs[0]);
        }

        // naive algorithm: do the n^2 multiplications, pack the results
        // into to_add and finally perform all the additions in batch
        let total_limbs = a.limbs.len() + b.limbs.len();
        let mut to_add = vec![vec![]; total_limbs];

        for (i, bi) in b.limbs.iter().enumerate() {
            // mul_biguint_u32 takes care of the carry propagation using mul_add_u32
            let res = self.mul_biguint_u32(a, *bi);
            for (j, &el) in res.limbs.iter().enumerate() {
                to_add[i + j].push(el);
            }
        }

        // additions in batch, with carry
        let mut combined_limbs = vec![to_add[0][0]];
        let (sum, mut carry) = self.add_u32(to_add[1][0], to_add[1][1]);
        combined_limbs.push(sum);
        for summands in &mut to_add.iter().skip(2) {
            let (new_result, new_carry) = self.add_u32s_with_carry(summands, carry);
            combined_limbs.push(new_result);
            carry = new_carry;
        }
        combined_limbs.push(carry);

        BigUintTarget {
            limbs: combined_limbs,
        }
    }

    fn mul_biguint_by_bool(&mut self, a: &BigUintTarget, b: BoolTarget) -> BigUintTarget {
        let t = b.target;

        BigUintTarget {
            limbs: a
                .limbs
                .iter()
                .map(|&l| U32Target(self.mul(l.0, t)))
                .collect(),
        }
    }

    fn mul_add_biguint(
        &mut self,
        x: &BigUintTarget,
        y: &BigUintTarget,
        z: &BigUintTarget,
    ) -> BigUintTarget {
        let prod = self.mul_biguint(x, y);
        self.add_biguint(&prod, z)
    }

    fn div_rem_biguint(
        &mut self,
        a: &BigUintTarget,
        b: &BigUintTarget,
    ) -> (BigUintTarget, BigUintTarget) {
        let a_len = a.limbs.len();
        let b_len = b.limbs.len();
        let div_num_limbs = if b_len > a_len + 1 {
            0
        } else {
            (a_len + 1) - b_len // Prevent overflow
        };
        let div = self.add_virtual_biguint_target(div_num_limbs);
        let rem = self.add_virtual_biguint_target(b_len);

        self.add_simple_generator(BigUintDivRemGenerator::<F, D> {
            a: a.clone(),
            b: b.clone(),
            div: div.clone(),
            rem: rem.clone(),
            _phantom: PhantomData,
        });

        let div_b = self.mul_biguint(&div, b);
        let div_b_plus_rem = self.add_biguint(&div_b, &rem);
        self.connect_biguint(a, &div_b_plus_rem);

        let cmp_rem_b = self.cmp_biguint(&rem, b);
        self.assert_one(cmp_rem_b.target);

        (div, rem)
    }

    fn div_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget {
        let (div, _rem) = self.div_rem_biguint(a, b);
        div
    }

    fn rem_biguint(&mut self, a: &BigUintTarget, b: &BigUintTarget) -> BigUintTarget {
        let (_div, rem) = self.div_rem_biguint(a, b);
        rem
    }

    fn range_check_biguint(&mut self, value: &BigUintTarget, order: &BigUintTarget) {
        let (value, order) = self.pad_biguints(value, order);
        let num_limbs = value.limbs.len();

        let mut borrow = self.zero_u32();
        for i in 0..num_limbs {
            let (_, new_borrow) = self.sub_u32(value.limbs[i], order.limbs[i], borrow);
            borrow = new_borrow;
        }

        let one = self.one_u32();
        self.connect_u32(borrow, one);
    }
}

pub trait WitnessBigUint<F: PrimeField64>: Witness<F> {
    fn get_biguint_target(&self, target: BigUintTarget) -> BigUint;
    fn set_biguint_target(&mut self, target: &BigUintTarget, value: &BigUint);
}

impl<T: Witness<F>, F: PrimeField64> WitnessBigUint<F> for T {
    fn get_biguint_target(&self, target: BigUintTarget) -> BigUint {
        target
            .limbs
            .into_iter()
            .rev()
            .fold(BigUint::zero(), |acc, limb| {
                (acc << 32) + self.get_target(limb.0).to_canonical_biguint()
            })
    }

    fn set_biguint_target(&mut self, target: &BigUintTarget, value: &BigUint) {
        let mut limbs = value.to_u32_digits();
        assert!(target.num_limbs() >= limbs.len());
        limbs.resize(target.num_limbs(), 0);
        for (i, &limb) in limbs.iter().enumerate() {
            self.set_u32_target(target.limbs[i], limb);
        }
    }
}

pub trait GeneratedValuesBigUint<F: PrimeField> {
    fn set_biguint_target(&mut self, target: &BigUintTarget, value: &BigUint)
    -> anyhow::Result<()>;
}

impl<F: PrimeField> GeneratedValuesBigUint<F> for GeneratedValues<F> {
    fn set_biguint_target(
        &mut self,
        target: &BigUintTarget,
        value: &BigUint,
    ) -> anyhow::Result<()> {
        let mut limbs = value.to_u32_digits();
        assert!(target.num_limbs() >= limbs.len());
        limbs.resize(target.num_limbs(), 0);
        for (i, &limb) in limbs.iter().enumerate() {
            self.set_u32_target(target.get_limb(i), limb)?;
        }
        Ok(())
    }
}

#[derive(Debug)]
struct BigUintDivRemGenerator<F: RichField + Extendable<D>, const D: usize> {
    a: BigUintTarget,
    b: BigUintTarget,
    div: BigUintTarget,
    rem: BigUintTarget,
    _phantom: PhantomData<F>,
}

impl<F: RichField + Extendable<D>, const D: usize> SimpleGenerator<F, D>
    for BigUintDivRemGenerator<F, D>
{
    fn dependencies(&self) -> Vec<Target> {
        self.a
            .limbs
            .iter()
            .chain(&self.b.limbs)
            .map(|&l| l.0)
            .collect()
    }

    fn run_once(
        &self,
        witness: &PartitionWitness<F>,
        out_buffer: &mut GeneratedValues<F>,
    ) -> anyhow::Result<()> {
        let a = witness.get_biguint_target(self.a.clone());
        let b = witness.get_biguint_target(self.b.clone());
        let (div, rem) = a.div_rem(&b);

        out_buffer.set_biguint_target(&self.div, &div)?;
        out_buffer.set_biguint_target(&self.rem, &rem)?;

        Ok(())
    }

    fn id(&self) -> String {
        todo!()
    }

    fn serialize(
        &self,
        _dst: &mut Vec<u8>,
        _common_data: &CommonCircuitData<F, D>,
    ) -> IoResult<()> {
        todo!()
    }

    fn deserialize(_src: &mut Buffer, _common_data: &CommonCircuitData<F, D>) -> IoResult<Self>
    where
        Self: Sized,
    {
        todo!()
    }
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use num::BigUint;
    use num::FromPrimitive;
    use num::Integer;
    use plonky2::iop::witness::PartialWitness;
    use plonky2::plonk::circuit_builder::CircuitBuilder;
    use plonky2::plonk::circuit_data::CircuitConfig;
    use plonky2::plonk::config::GenericConfig;
    use plonky2::plonk::config::PoseidonGoldilocksConfig;
    use rand::Rng;
    use rand::rngs::OsRng;

    use crate::gadgets::biguint::CircuitBuilderBiguint;
    use crate::gadgets::biguint::WitnessBigUint;

    #[test]
    fn test_biguint_add() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut rng = OsRng;

        let x_value = BigUint::from_u128(rng.r#gen()).unwrap();
        let y_value = BigUint::from_u128(rng.r#gen()).unwrap();
        let expected_z_value = &x_value + &y_value;

        let config = CircuitConfig::standard_recursion_config();
        let mut pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let x = builder.add_virtual_biguint_target(x_value.to_u32_digits().len());
        let y = builder.add_virtual_biguint_target(y_value.to_u32_digits().len());
        let z = builder.add_biguint(&x, &y);
        let expected_z = builder.add_virtual_biguint_target(expected_z_value.to_u32_digits().len());
        builder.connect_biguint(&z, &expected_z);

        pw.set_biguint_target(&x, &x_value);
        pw.set_biguint_target(&y, &y_value);
        pw.set_biguint_target(&expected_z, &expected_z_value);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();
        data.verify(proof)
    }

    #[test]
    fn test_biguint_sub() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut rng = OsRng;

        let mut x_value = BigUint::from_u128(rng.r#gen()).unwrap();
        let mut y_value = BigUint::from_u128(rng.r#gen()).unwrap();
        if y_value > x_value {
            (x_value, y_value) = (y_value, x_value);
        }
        let expected_z_value = &x_value - &y_value;

        let config = CircuitConfig::standard_recursion_config();
        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let x = builder.constant_biguint(&x_value);
        let y = builder.constant_biguint(&y_value);
        let z = builder.sub_biguint(&x, &y);
        let expected_z = builder.constant_biguint(&expected_z_value);

        builder.connect_biguint(&z, &expected_z);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();
        data.verify(proof)
    }

    #[test]
    fn test_biguint_mul() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut rng = OsRng;

        let x_value = BigUint::from_u128(rng.r#gen()).unwrap();
        let y_value = BigUint::from_u128(rng.r#gen()).unwrap();
        let expected_z_value = &x_value * &y_value;

        let config = CircuitConfig::standard_ecc_config();
        let mut pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let x = builder.add_virtual_biguint_target(x_value.to_u32_digits().len());
        let y = builder.add_virtual_biguint_target(y_value.to_u32_digits().len());
        let z = builder.mul_biguint(&x, &y);
        let expected_z = builder.add_virtual_biguint_target(expected_z_value.to_u32_digits().len());
        builder.connect_biguint(&z, &expected_z);

        pw.set_biguint_target(&x, &x_value);
        pw.set_biguint_target(&y, &y_value);
        pw.set_biguint_target(&expected_z, &expected_z_value);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();
        data.verify(proof)
    }

    #[test]
    fn test_biguint_cmp() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut rng = OsRng;

        let x_value = BigUint::from_u128(rng.r#gen()).unwrap();
        let y_value = BigUint::from_u128(rng.r#gen()).unwrap();

        let config = CircuitConfig::standard_recursion_config();
        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let x = builder.constant_biguint(&x_value);
        let y = builder.constant_biguint(&y_value);
        let cmp = builder.cmp_biguint(&x, &y);
        let expected_cmp = builder.constant_bool(x_value <= y_value);

        builder.connect(cmp.target, expected_cmp.target);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();
        data.verify(proof)
    }

    #[test]
    fn test_biguint_div_rem() -> Result<()> {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;
        let mut rng = OsRng;

        let mut x_value = BigUint::from_u128(rng.r#gen()).unwrap();
        let mut y_value = BigUint::from_u128(rng.r#gen()).unwrap();
        if y_value > x_value {
            (x_value, y_value) = (y_value, x_value);
        }
        let (expected_div_value, expected_rem_value) = x_value.div_rem(&y_value);

        let config = CircuitConfig::standard_recursion_config();
        let pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let x = builder.constant_biguint(&x_value);
        let y = builder.constant_biguint(&y_value);
        let (div, rem) = builder.div_rem_biguint(&x, &y);

        let expected_div = builder.constant_biguint(&expected_div_value);
        let expected_rem = builder.constant_biguint(&expected_rem_value);

        builder.connect_biguint(&div, &expected_div);
        builder.connect_biguint(&rem, &expected_rem);

        let data = builder.build::<C>();
        let proof = data.prove(pw).unwrap();
        data.verify(proof)
    }

    #[test]
    fn test_biguint_range_check() {
        const D: usize = 2;
        type C = PoseidonGoldilocksConfig;
        type F = <C as GenericConfig<D>>::F;

        let config = CircuitConfig::standard_recursion_config();
        let mut pw = PartialWitness::new();
        let mut builder = CircuitBuilder::<F, D>::new(config);

        let biguint_target = builder.add_virtual_biguint_target(2);
        let order = 0xFF0000000000000u128;
        let order_target = builder.constant_biguint(&BigUint::from(order));
        builder.range_check_biguint(&biguint_target, &order_target);
        pw.set_biguint_target(&biguint_target, &BigUint::from(order));

        let data = builder.build::<C>();
        let result = data.prove(pw);

        assert!(result.is_err());
    }
}
