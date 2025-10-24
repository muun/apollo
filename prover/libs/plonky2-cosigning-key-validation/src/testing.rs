#[cfg(test)]
mod tests {
    use crate::*;

    const HPKE_EPHEMERAL_PRIVATE_KEY: &str =
        "bb611d7aa2a5688d947085fa5c60e87fb051042854c23fff388945dc7010b0b5";
    const HPKE_EPHEMERAL_PUBLIC_KEY: &str = "0471b55503fb340ec6c202d6cdce7d49c365b78ae2fa3bab06ae87553610006553441e4f7ad3c3c834b0e0538ac241e2adc61c85a10ec7341eb1129edb0caccd0a";

    #[allow(dead_code)]
    const RECOVERY_CODE_PRIVATE_KEY: &str =
        "20f5dccb488fe31f95ba0f55ed306df9df2a5a171157838bada35342e71f5d7f";

    const RECOVERY_CODE_PUBLIC_KEY: &str = "04dc5489ca59d23d4deebc778850651da1f3da1c505db198df8e5cf9fe322964c7c5ab62cac0b255be7d75606e04bc8015e70c39d6e0d6faaf435eb92c29043ded";

    const PLAINTEXT_SCALAR: &str =
        "f9fff35fb0004862359e69bcbb003b0dc8e610e6d82af40a25ed5d75386241df";

    const PLAINTEXT_PUBLIC_KEY: &str = "0468a18701d75331dddbef334c070931cf3561288e78346666fdcc01fb28aac0f17823d00b35cd06eb0508067a345027ab03a716ea825220059a168c6a6d5090db";

    const CIPHERTEXT: &str = "23d170accd4b2849fbfa0e8e49f753eefb274c0449ab8ab46e9f35a4e2265f054d7cbab020157c34c5ba61e0e7695608";

    fn str_to_arr<const N: usize>(s: &str) -> [u8; N] {
        hex::decode(s).unwrap().try_into().unwrap()
    }

    fn timed<T, F: FnOnce() -> T>(name: &str, f: F) -> T {
        let before = std::time::Instant::now();
        let result = f();
        let after = std::time::Instant::now();
        eprintln!("{name} took {:?}", after.duration_since(before));
        result
    }

    #[test]
    fn test_prove_verify() {
        env_logger::builder()
            .is_test(true) // this sets default to debug + disables timestamps
            .try_init()
            .ok();

        let (prover_data, verifier_data) = timed("precompute", precompute);

        let proof = timed("prove", || {
            prove(&prover_data, &ProverInputs {
                hpke_ephemeral_private_key: str_to_arr(HPKE_EPHEMERAL_PRIVATE_KEY),
                hpke_ephemeral_public_key: str_to_arr(HPKE_EPHEMERAL_PUBLIC_KEY),
                recovery_code_public_key: str_to_arr(RECOVERY_CODE_PUBLIC_KEY),
                plaintext_scalar: str_to_arr(PLAINTEXT_SCALAR),
                plaintext_public_key: str_to_arr(PLAINTEXT_PUBLIC_KEY),
                ciphertext: str_to_arr(CIPHERTEXT),
            })
            .unwrap()
        });

        timed("verify", || {
            verify(&verifier_data, proof, &VerifierInputs {
                hpke_ephemeral_public_key: str_to_arr(HPKE_EPHEMERAL_PUBLIC_KEY),
                recovery_code_public_key: str_to_arr(RECOVERY_CODE_PUBLIC_KEY),
                plaintext_public_key: str_to_arr(PLAINTEXT_PUBLIC_KEY),
                ciphertext: str_to_arr(CIPHERTEXT),
            })
            .unwrap()
        });
    }

    #[test]
    fn test_comparing_circuits_constants() {
        let verifier1 = precompute().1;
        let verifier2 = precompute().1;

        assert_eq!(verifier1.serialize(), verifier2.serialize());
    }

    #[test]
    #[should_panic]
    fn incorrect_ephemeral_private_key() {
        let (prover_data, _) = precompute();

        prove(&prover_data, &ProverInputs {
            hpke_ephemeral_private_key: str_to_arr(
                "ad9243a485da16dc53d4cdbbf9974f55a6b4d8563eaacffdbd32890e0df3e976",
            ),
            hpke_ephemeral_public_key: str_to_arr(HPKE_EPHEMERAL_PUBLIC_KEY),
            recovery_code_public_key: str_to_arr(RECOVERY_CODE_PUBLIC_KEY),
            plaintext_scalar: str_to_arr(PLAINTEXT_SCALAR),
            plaintext_public_key: str_to_arr(PLAINTEXT_PUBLIC_KEY),
            ciphertext: str_to_arr(CIPHERTEXT),
        })
        .unwrap();
    }

    #[test]
    #[should_panic]
    fn incorrect_ephemeral_public_key() {
        let (prover_data, _) = precompute();

        prove(&prover_data, &ProverInputs {
            hpke_ephemeral_private_key: str_to_arr(HPKE_EPHEMERAL_PRIVATE_KEY),
            hpke_ephemeral_public_key: str_to_arr("04a537b6bb21a11d8662317659e2b2a27aa37a4e95b5ececa1b3fdd4a7a772dac8047073a501394fd623a5747b25bbc0a16e934ba865423df840b35a9a5019f510"),
            recovery_code_public_key: str_to_arr(RECOVERY_CODE_PUBLIC_KEY),
            plaintext_scalar: str_to_arr(PLAINTEXT_SCALAR),
            plaintext_public_key: str_to_arr(PLAINTEXT_PUBLIC_KEY),
            ciphertext: str_to_arr(CIPHERTEXT),
        })
            .unwrap();
    }

    #[test]
    #[should_panic]
    fn incorrect_recovery_code_public_key() {
        let (prover_data, _) = precompute();

        prove(&prover_data, &ProverInputs {
            hpke_ephemeral_private_key: str_to_arr(HPKE_EPHEMERAL_PRIVATE_KEY),
            hpke_ephemeral_public_key: str_to_arr(HPKE_EPHEMERAL_PUBLIC_KEY),
            recovery_code_public_key: str_to_arr("04465538823cd6f5438db12bc9b57a35c0f19e5787b517502719da3fe63597566284056b4a89937f529ad7e93a76f3e894d6572db3ef493d29f7d05f2b5ff1939b"),
            plaintext_scalar: str_to_arr(PLAINTEXT_SCALAR),
            plaintext_public_key: str_to_arr(PLAINTEXT_PUBLIC_KEY),
            ciphertext: str_to_arr(CIPHERTEXT),
        })
            .unwrap();
    }

    #[test]
    #[should_panic]
    fn test_incorrect_ciphertext() {
        let (prover_data, _) = precompute();

        prove(&prover_data, &ProverInputs {
            hpke_ephemeral_private_key: str_to_arr(HPKE_EPHEMERAL_PRIVATE_KEY),
            hpke_ephemeral_public_key: str_to_arr(HPKE_EPHEMERAL_PUBLIC_KEY),
            recovery_code_public_key: str_to_arr(RECOVERY_CODE_PUBLIC_KEY),
            plaintext_scalar: str_to_arr(PLAINTEXT_SCALAR),
            plaintext_public_key: str_to_arr(PLAINTEXT_PUBLIC_KEY),
            ciphertext: str_to_arr("34bfe3253dde55ec3fbe27a9f8cf91293ba40822107f2736eb4fc0228716f320450e78057c76aea4177c6a008ab7b57f"),
        })
            .unwrap();
    }
}
