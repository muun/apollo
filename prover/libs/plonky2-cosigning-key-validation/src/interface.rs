use anyhow::Result;
use anyhow::anyhow;
use plonky2::field::extension::Extendable;
use plonky2::field::goldilocks_field::GoldilocksField;
use plonky2::field::types::Field;
use plonky2::gates::arithmetic_base::ArithmeticGate;
use plonky2::gates::arithmetic_extension::ArithmeticExtensionGate;
use plonky2::gates::base_sum::BaseSumGate;
use plonky2::gates::constant::ConstantGate;
use plonky2::gates::coset_interpolation::CosetInterpolationGate;
use plonky2::gates::exponentiation::ExponentiationGate;
use plonky2::gates::lookup::LookupGate;
use plonky2::gates::lookup_table::LookupTableGate;
use plonky2::gates::multiplication_extension::MulExtensionGate;
use plonky2::gates::noop::NoopGate;
use plonky2::gates::poseidon::PoseidonGate;
use plonky2::gates::poseidon_mds::PoseidonMdsGate;
use plonky2::gates::public_input::PublicInputGate;
use plonky2::gates::random_access::RandomAccessGate;
use plonky2::gates::reducing::ReducingGate;
use plonky2::gates::reducing_extension::ReducingExtensionGate;
use plonky2::get_gate_tag_impl;
use plonky2::hash::hash_types::RichField;
use plonky2::impl_gate_serializer;
use plonky2::plonk::circuit_data::ProverCircuitData;
use plonky2::plonk::circuit_data::VerifierCircuitData;
use plonky2::plonk::config::KeccakGoldilocksConfig;
use plonky2::plonk::proof::CompressedProofWithPublicInputs;
use plonky2::read_gate_impl;
use plonky2::util::serialization::GateSerializer;
use plonky2_precomputed_windowed_mul::from_compressed_private_key;
use plonky2_precomputed_windowed_mul::from_uncompressed_public_key;
use plonky2_u32::gates::add_many_u32::U32AddManyGate;
use plonky2_u32::gates::arithmetic_u32::U32ArithmeticGate;
use plonky2_u32::gates::comparison::ComparisonGate;
use plonky2_u32::gates::range_check_u32::U32RangeCheckGate;
use plonky2_u32::gates::subtraction_u32::U32SubtractionGate;

use crate::circuit::Circuit;
use crate::inputs::CIPHERTEXT_LENGTH_IN_BYTES;
use crate::inputs::Inputs;
use crate::inputs::PRIVATE_KEY_LENGTH_IN_BYTES;
use crate::inputs::PublicInputs;
use crate::inputs::TargetInputs;
use crate::inputs::UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES;

pub struct ProverData {
    target_inputs: TargetInputs,
    circuit_data: ProverCircuitData<GoldilocksField, KeccakGoldilocksConfig, 2>,
}

pub struct VerifierData {
    circuit_data: VerifierCircuitData<GoldilocksField, KeccakGoldilocksConfig, 2>,
}

pub struct ProverInputs {
    pub hpke_ephemeral_private_key: [u8; PRIVATE_KEY_LENGTH_IN_BYTES],
    pub hpke_ephemeral_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
    pub recovery_code_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
    pub plaintext_scalar: [u8; PRIVATE_KEY_LENGTH_IN_BYTES],
    pub plaintext_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
    pub ciphertext: [u8; CIPHERTEXT_LENGTH_IN_BYTES],
}

pub struct VerifierInputs {
    pub hpke_ephemeral_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
    pub recovery_code_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
    pub plaintext_public_key: [u8; UNCOMPRESSED_PUBLIC_KEY_LENGTH_IN_BYTES],
    pub ciphertext: [u8; CIPHERTEXT_LENGTH_IN_BYTES],
}

pub struct Proof(pub Vec<u8>);

const GOLDILOCKS_FIELD_BYTES: usize = GoldilocksField::BITS / 8;

pub fn precompute() -> (ProverData, VerifierData) {
    let circuit = Circuit::build();
    let verifier_data = circuit.circuit.verifier_data();
    (
        ProverData {
            target_inputs: circuit.target_inputs,
            circuit_data: circuit.circuit.prover_data(),
        },
        VerifierData {
            circuit_data: verifier_data,
        },
    )
}

pub fn prove(data: &ProverData, inputs: &ProverInputs) -> Result<Proof> {
    let partial_witness = data.target_inputs.set_inputs(&Inputs::new(
        from_compressed_private_key(inputs.hpke_ephemeral_private_key),
        inputs.hpke_ephemeral_public_key,
        from_uncompressed_public_key(inputs.recovery_code_public_key)?,
        from_compressed_private_key(inputs.plaintext_scalar),
        from_uncompressed_public_key(inputs.plaintext_public_key)?,
        inputs.ciphertext,
    ));

    let proof = data.circuit_data.prove(partial_witness)?;

    let public_inputs_len_bytes = GOLDILOCKS_FIELD_BYTES * proof.public_inputs.len();

    let proof_bytes = proof
        .compress(
            &data.circuit_data.prover_only.circuit_digest,
            &data.circuit_data.common,
        )?
        .to_bytes();

    let proof_len_bytes = proof_bytes.len();

    let proof_without_public_inputs_len_bytes = proof_len_bytes - public_inputs_len_bytes;

    Ok(Proof(
        proof_bytes[..proof_without_public_inputs_len_bytes].to_vec(),
    ))
}

pub fn verify(data: &VerifierData, proof: Proof, inputs: &VerifierInputs) -> Result<()> {
    let inputs = PublicInputs::new(
        inputs.hpke_ephemeral_public_key,
        from_uncompressed_public_key(inputs.plaintext_public_key)?,
        from_uncompressed_public_key(inputs.recovery_code_public_key)?,
        inputs.ciphertext,
    );

    let public_inputs = inputs.prepare_public_inputs()?;

    let mut proof_bytes: Vec<u8> = proof.0.clone();

    proof_bytes.extend(public_inputs);

    let proof =
        CompressedProofWithPublicInputs::from_bytes(proof_bytes, &data.circuit_data.common)?;

    data.circuit_data.verify_compressed(proof)?;

    Ok(())
}

impl VerifierData {
    pub fn serialize(&self) -> Vec<u8> {
        self.circuit_data.to_bytes(&TestGateSerializer).unwrap()
    }

    pub fn deserialize(bytes: &[u8]) -> Result<Self> {
        let circuit_data = VerifierCircuitData::from_bytes(bytes.to_vec(), &TestGateSerializer)
            .map_err(|e| anyhow!(e.to_string()))?;

        Ok(VerifierData { circuit_data })
    }
}

pub struct TestGateSerializer;
impl<F: RichField + Extendable<D>, const D: usize> GateSerializer<F, D> for TestGateSerializer {
    impl_gate_serializer! {
        TestGateSerializer,
        ArithmeticGate,
        ArithmeticExtensionGate<D>,
        BaseSumGate<2>,
        BaseSumGate<4>,
        ConstantGate,
        CosetInterpolationGate<F, D>,
        ExponentiationGate<F, D>,
        LookupGate,
        LookupTableGate,
        MulExtensionGate<D>,
        NoopGate,
        PoseidonMdsGate<F, D>,
        PoseidonGate<F, D>,
        PublicInputGate,
        RandomAccessGate<F, D>,
        ReducingExtensionGate<D>,
        ReducingGate<D>,
        ComparisonGate<F, D>,
        U32AddManyGate<F, D>,
        U32ArithmeticGate<F, D>,
        U32RangeCheckGate<F, D>,
        U32SubtractionGate<F, D>
    }
}
