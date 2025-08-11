#[cfg(test)]
mod tests {
    use crate::*;

    const HPKE_EPHEMERAL_PRIVATE_KEY: &str =
        "ad9243a485da16dc53d4cdbbf9974f55a6b4d8563eaacffdbd32890e0df3e975";
    const HPKE_EPHEMERAL_PUBLIC_KEY: &str = "04197175cf54953d5637aaa6d9f12424d8fc708e826cf5d3442298f3c6545cfc35ab37898edf5457e4823bf35a8a08a0088236fc9194e048cd1345ca0d4f3afa8f";

    #[allow(dead_code)]
    const RECOVERY_CODE_PRIVATE_KEY: &str =
        "cb027d62281abf5c7c7140477579df6c06e58c04a40c71354018b414af30a2e9";

    const RECOVERY_CODE_PUBLIC_KEY: &str = "04465538823cd6f5438db12bc9b57a35c0f19e5787b517502719da3fe63597566284056b4a89937f529ad7e93a76f3e894d6572db3ef493d29f7d05f2b5ff1939a";

    const PLAINTEXT_SCALAR: &str =
        "f165aaaa334cd690be86cdfe7a53eca573482c18c6360519e9e3a016e1f6442a";

    const PLAINTEXT_PUBLIC_KEY: &str = "04a537b6bb21a11d8662317659e2b2a27aa37a4e95b5ececa1b3fdd4a7a772dac8047073a501394fd623a5747b25bbc0a16e934ba865423df840b35a9a5019f511";

    const CIPHERTEXT: &str = "34bfe3253dde55ec3fbe27a9f8cf91293ba40822107f2736eb4fc0228716f320450e78057c76aea4177c6a008ab7b57e";

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
