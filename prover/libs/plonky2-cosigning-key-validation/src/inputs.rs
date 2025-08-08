use anyhow::Error;
use plonky2::field::secp256k1_base::Secp256K1Base;
use plonky2::field::secp256k1_scalar::Secp256K1Scalar;
use plonky2::iop::witness::PartialWitness;
use plonky2_bytes::ByteTarget;
use plonky2_bytes::WitnessBytes;
use plonky2_bytes::ith_most_significant_bit;
use plonky2_ecdsa::curve::curve_types::AffinePoint;
use plonky2_ecdsa::curve::secp256k1::Secp256K1;
use plonky2_ecdsa::gadgets::curve::AffinePointTarget;
use plonky2_ecdsa::gadgets::curve::WitnessPoint;
use plonky2_ecdsa::gadgets::nonnative::NonNativeTarget;
use plonky2_ecdsa::gadgets::nonnative::WitnessNonNative;
use plonky2_precomputed_windowed_mul::PrecomputedWindowedMulTarget;
use plonky2_precomputed_windowed_mul::WitnessPrecomputedWindowedMul;
use plonky2_precomputed_windowed_mul::precompute_mul_table;

use crate::circuit::F;

pub const UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES: usize = 65;
pub const PRIVATE_KEY_LENGTH_IN_BYTES: usize = 32;
pub const POLY1305_TAG_LENGTH_IN_BYTES: usize = 16;

pub const CIPHERTEXT_LENGTH_IN_BYTES: usize =
    PRIVATE_KEY_LENGTH_IN_BYTES + POLY1305_TAG_LENGTH_IN_BYTES;

#[derive(Clone)]
pub struct TargetInputs {
    hpke_ephemeral_private_key: NonNativeTarget<Secp256K1Scalar>,
    hpke_ephemeral_public_key: [ByteTarget; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
    plaintext_scalar: NonNativeTarget<Secp256K1Scalar>,
    plaintext_public_key: AffinePointTarget<Secp256K1>,
    recovery_code_precomputed_windowed_mul: PrecomputedWindowedMulTarget,
    ciphertext: [ByteTarget; CIPHERTEXT_LENGTH_IN_BYTES],
}

impl TargetInputs {
    pub fn new(
        hpke_ephemeral_private_key: NonNativeTarget<Secp256K1Scalar>,
        hpke_ephemeral_public_key: [ByteTarget; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
        plaintext_scalar: NonNativeTarget<Secp256K1Scalar>,
        plaintext_public_key: AffinePointTarget<Secp256K1>,
        recovery_code_precomputed_windowed_mul: PrecomputedWindowedMulTarget,
        ciphertext: [ByteTarget; CIPHERTEXT_LENGTH_IN_BYTES],
    ) -> Self {
        Self {
            hpke_ephemeral_private_key,
            hpke_ephemeral_public_key,
            plaintext_scalar,
            plaintext_public_key,
            recovery_code_precomputed_windowed_mul,
            ciphertext,
        }
    }

    pub fn set_inputs(&self, inputs: &Inputs) -> PartialWitness<F> {
        let mut pw = PartialWitness::new();

        pw.set_non_native_target(
            &self.hpke_ephemeral_private_key,
            &inputs.hpke_ephemeral_private_key,
        );

        _ = pw.set_bytes(
            &self.hpke_ephemeral_public_key,
            &inputs.public_inputs.hpke_ephemeral_public_key,
        );

        pw.set_non_native_target(&self.plaintext_scalar, &inputs.plaintext_scalar);

        pw.set_affine_point_target(
            &self.plaintext_public_key,
            &inputs.public_inputs.plaintext_public_key,
        );

        pw.set_precomputed_windowed_mul(
            &self.recovery_code_precomputed_windowed_mul,
            &inputs.public_inputs.recovery_code_public_key,
        );

        _ = pw.set_bytes(&self.ciphertext, &inputs.public_inputs.ciphertext);

        pw
    }
}

pub struct Inputs {
    hpke_ephemeral_private_key: Secp256K1Scalar,
    plaintext_scalar: Secp256K1Scalar,
    public_inputs: PublicInputs,
}

impl Inputs {
    pub fn new(
        hpke_ephemeral_private_key: Secp256K1Scalar,
        hpke_ephemeral_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
        recovery_code_public_key: AffinePoint<Secp256K1>,
        plaintext_scalar: Secp256K1Scalar,
        plaintext_public_key: AffinePoint<Secp256K1>,
        ciphertext: [u8; CIPHERTEXT_LENGTH_IN_BYTES],
    ) -> Self {
        Self {
            hpke_ephemeral_private_key,
            plaintext_scalar,
            public_inputs: PublicInputs::new(
                hpke_ephemeral_public_key,
                plaintext_public_key,
                recovery_code_public_key,
                ciphertext,
            ),
        }
    }
}

pub struct PublicInputs {
    pub hpke_ephemeral_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
    pub plaintext_public_key: AffinePoint<Secp256K1>,
    pub recovery_code_public_key: AffinePoint<Secp256K1>,
    pub ciphertext: [u8; CIPHERTEXT_LENGTH_IN_BYTES],
}

impl PublicInputs {
    pub fn new(
        hpke_ephemeral_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
        plaintext_public_key: AffinePoint<Secp256K1>,
        recovery_code_public_key: AffinePoint<Secp256K1>,
        ciphertext: [u8; CIPHERTEXT_LENGTH_IN_BYTES],
    ) -> Self {
        Self {
            hpke_ephemeral_public_key,
            plaintext_public_key,
            recovery_code_public_key,
            ciphertext,
        }
    }

    /// The public inputs data for our circuit is big due to the PrecomputedWindowedMul data for the
    /// recovery code. We discard the public inputs from the ProofWithPublicInputs to keep the proof
    /// payload small enough to send it to the client. The prepare_public_inputs method allows to
    /// reconstruct the data that we discarded which is later needed at verification time.
    pub fn prepare_public_inputs(&self) -> Result<Vec<u8>, Error> {
        let mut public_inputs: Vec<u8> = Vec::new();

        Self::prepare_byte_targets(&self.hpke_ephemeral_public_key, &mut public_inputs)?;

        Self::prepare_affine_point_target(&self.plaintext_public_key, &mut public_inputs)?;

        Self::prepare_precomputed_windowed_mul_target(
            &self.recovery_code_public_key,
            &mut public_inputs,
        )?;

        Self::prepare_byte_targets(&self.ciphertext, &mut public_inputs)?;

        Ok(public_inputs)
    }

    fn prepare_target(target: u64, public_inputs: &mut Vec<u8>) -> Result<(), Error> {
        // A Target's value is an u64 which is serialized by taking its little endian bytes.

        public_inputs.extend(target.to_le_bytes());

        Ok(())
    }

    fn prepare_bool_target(bool_target: bool, public_inputs: &mut Vec<u8>) -> Result<(), Error> {
        // A BoolTarget stores a Target constrained to be 0 or 1.

        Self::prepare_target(bool_target as u64, public_inputs)?;

        Ok(())
    }

    fn prepare_byte_target(byte: u8, public_inputs: &mut Vec<u8>) -> Result<(), Error> {
        // A ByteTarget is represented as a list of 8 BoolTargets.
        for i in 0..8 {
            Self::prepare_bool_target(ith_most_significant_bit(byte, i), public_inputs)?;
        }

        Ok(())
    }

    fn prepare_byte_targets(bytes: &[u8], public_inputs: &mut Vec<u8>) -> Result<(), Error> {
        for byte in bytes {
            Self::prepare_byte_target(*byte, public_inputs)?
        }

        Ok(())
    }

    fn prepare_u32_target(value: u32, public_inputs: &mut Vec<u8>) -> Result<(), Error> {
        // An U32Target stores a Target constrained between 0 and 2^32.

        Self::prepare_target(value as u64, public_inputs)?;

        Ok(())
    }

    fn prepare_biguint_target(limbs: Vec<u32>, public_inputs: &mut Vec<u8>) -> Result<(), Error> {
        // A BigUintTarget is internally represented as a list of U32Target limbs.

        for limb in limbs {
            Self::prepare_u32_target(limb, public_inputs)?;
        }

        Ok(())
    }

    fn prepare_nonnative_target(
        scalar: Secp256K1Base,
        public_inputs: &mut Vec<u8>,
    ) -> Result<(), Error> {
        // A (256 bit) NonNativeTarget is internally represented as an 8 limb BigUintTarget.
        let base: u64 = 1 << 32;
        let mut limbs: Vec<u32> = Vec::new();
        for limb in scalar.0 {
            limbs.push((limb % base) as u32);
            limbs.push((limb / base) as u32);
        }

        Self::prepare_biguint_target(limbs, public_inputs)
    }

    fn prepare_affine_point_target(
        public_key: &AffinePoint<Secp256K1>,
        public_inputs: &mut Vec<u8>,
    ) -> Result<(), Error> {
        // An AffinePointTarget consists of two coordinates which are NonNativeTargets.
        Self::prepare_nonnative_target(public_key.x, public_inputs)?;
        Self::prepare_nonnative_target(public_key.y, public_inputs)?;
        Ok(())
    }

    fn prepare_precomputed_windowed_mul_target(
        public_key: &AffinePoint<Secp256K1>,
        public_inputs: &mut Vec<u8>,
    ) -> Result<(), Error> {
        // A PrecomputedWindowedMul is a two-dimensional array of AffinePointTargets.

        let table_data = precompute_mul_table(*public_key);

        for row in table_data.iter() {
            for entry in row.iter() {
                Self::prepare_affine_point_target(entry, public_inputs)?;
            }
        }

        Ok(())
    }
}
