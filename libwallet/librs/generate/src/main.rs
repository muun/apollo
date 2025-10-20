use std::fs;

use cosigning_key_validation::ProverInputs;
use cosigning_key_validation::prove;

fn str_to_arr<const N: usize>(s: &str) -> [u8; N] {
    hex::decode(s).unwrap().try_into().unwrap()
}

const HPKE_EPHEMERAL_PRIVATE_KEY: &str =
    "bb611d7aa2a5688d947085fa5c60e87fb051042854c23fff388945dc7010b0b5";
const HPKE_EPHEMERAL_PUBLIC_KEY: &str = "0471b55503fb340ec6c202d6cdce7d49c365b78ae2fa3bab06ae87553610006553441e4f7ad3c3c834b0e0538ac241e2adc61c85a10ec7341eb1129edb0caccd0a";

#[allow(dead_code)]
const RECOVERY_CODE_PRIVATE_KEY: &str =
    "20f5dccb488fe31f95ba0f55ed306df9df2a5a171157838bada35342e71f5d7f";

const RECOVERY_CODE_PUBLIC_KEY: &str = "04dc5489ca59d23d4deebc778850651da1f3da1c505db198df8e5cf9fe322964c7c5ab62cac0b255be7d75606e04bc8015e70c39d6e0d6faaf435eb92c29043ded";

const PLAINTEXT_SCALAR: &str = "f9fff35fb0004862359e69bcbb003b0dc8e610e6d82af40a25ed5d75386241df";

const PLAINTEXT_PUBLIC_KEY: &str = "0468a18701d75331dddbef334c070931cf3561288e78346666fdcc01fb28aac0f17823d00b35cd06eb0508067a345027ab03a716ea825220059a168c6a6d5090db";

const CIPHERTEXT: &str = "23d170accd4b2849fbfa0e8e49f753eefb274c0449ab8ab46e9f35a4e2265f054d7cbab020157c34c5ba61e0e7695608";

fn main() {
    let (prover_data, verifier_data) = cosigning_key_validation::precompute();

    let proof = prove(&prover_data, &ProverInputs {
        hpke_ephemeral_private_key: str_to_arr(HPKE_EPHEMERAL_PRIVATE_KEY),
        hpke_ephemeral_public_key: str_to_arr(HPKE_EPHEMERAL_PUBLIC_KEY),
        recovery_code_public_key: str_to_arr(RECOVERY_CODE_PUBLIC_KEY),
        plaintext_scalar: str_to_arr(PLAINTEXT_SCALAR),
        plaintext_public_key: str_to_arr(PLAINTEXT_PUBLIC_KEY),
        ciphertext: str_to_arr(CIPHERTEXT),
    })
    .unwrap();
    fs::write("test_proof.bin", proof.0).unwrap();

    fs::write(
        "bindings/src/bin/verifier_data.bin",
        verifier_data.serialize(),
    )
    .unwrap();
}
