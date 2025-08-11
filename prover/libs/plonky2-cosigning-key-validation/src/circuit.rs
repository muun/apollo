use log::debug;
use plonky2::field::secp256k1_scalar::Secp256K1Scalar;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2::plonk::circuit_data::CircuitConfig;
use plonky2::plonk::circuit_data::CircuitData;
use plonky2::plonk::config::GenericConfig;
use plonky2::plonk::config::KeccakGoldilocksConfig;
use plonky2_bitcoin_hpke::encoding::serialize_private_key;
use plonky2_bitcoin_hpke::single_shot;
use plonky2_bytes::CircuitBuilderBytes;
use plonky2_ecdsa::curve::secp256k1::Secp256K1;
use plonky2_ecdsa::gadgets::curve::AffinePointTarget;
use plonky2_ecdsa::gadgets::curve::CircuitBuilderCurve;
use plonky2_ecdsa::gadgets::nonnative::CircuitBuilderNonNative;
use plonky2_ecdsa::gadgets::nonnative::NonNativeTarget;
use plonky2_precomputed_windowed_mul::CircuitBuilderPrecomputedWindowedMul;

use crate::inputs::TargetInputs;

pub const D: usize = 2;
pub type C = KeccakGoldilocksConfig;
pub type F = <C as GenericConfig<D>>::F;

pub const INFO: &str = "muun.com/cosigning-key/2/2";
pub const AAD: &str = "";

pub struct Circuit {
    pub circuit: CircuitData<F, C, D>,
    pub target_inputs: TargetInputs,
}

impl Circuit {
    pub fn build() -> Self {
        let config = CircuitConfig {
            zero_knowledge: true,
            num_wires: 190,
            ..CircuitConfig::standard_recursion_config()
        };

        let mut builder = CircuitBuilder::<F, D>::new(config);

        let hpke_ephemeral_private_key: NonNativeTarget<Secp256K1Scalar> =
            builder.add_virtual_nonnative_target();

        let plaintext_scalar: NonNativeTarget<Secp256K1Scalar> =
            builder.add_virtual_nonnative_target();

        let recovery_code_precomputed_windowed_mul =
            builder.add_virtual_precomputed_windowed_mul_target();

        let info = builder.constant_bytes(INFO.as_bytes());
        let aad = builder.constant_bytes(AAD.as_bytes());

        let plaintext_scalar_bytes = serialize_private_key(&mut builder, plaintext_scalar.clone());

        let (ciphertext, hpke_ephemeral_public_key) = single_shot(
            &mut builder,
            &hpke_ephemeral_private_key,
            &recovery_code_precomputed_windowed_mul,
            &info,
            &aad,
            &plaintext_scalar_bytes,
        );

        let plaintext_public_key = Self::get_public_key(&mut builder, &plaintext_scalar);

        // Register public inputs
        builder.register_public_bytes(&hpke_ephemeral_public_key);
        builder.register_public_affine_point(&plaintext_public_key);
        builder.register_public_precomputed_windowed_mul(&recovery_code_precomputed_windowed_mul);
        builder.register_public_bytes(ciphertext.as_slice());

        let inputs = TargetInputs::new(
            hpke_ephemeral_private_key,
            hpke_ephemeral_public_key.try_into().unwrap(),
            plaintext_scalar,
            plaintext_public_key,
            recovery_code_precomputed_windowed_mul,
            ciphertext.try_into().unwrap(),
        );

        debug!("num_gates = {}", builder.num_gates());

        Self {
            circuit: builder.build::<C>(),
            target_inputs: inputs,
        }
    }

    fn get_public_key(
        builder: &mut CircuitBuilder<F, D>,
        private_key: &NonNativeTarget<Secp256K1Scalar>,
    ) -> AffinePointTarget<Secp256K1> {
        let basepoint_mul_table = builder.add_basepoint_precomputed_windowed_mul_target();

        builder.scalar_mul(&basepoint_mul_table, private_key)
    }
}
