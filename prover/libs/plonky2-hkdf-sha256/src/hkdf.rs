use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_bytes::ByteTarget;
use plonky2_bytes::CircuitBuilderBytes;

use crate::hmac::OutputSize;
use crate::hmac::hmac_sha256;

// Implementation of https://www.rfc-editor.org/rfc/rfc5869.html#section-2.2
pub fn hkdf_sha256_extract<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    salt: &[ByteTarget],
    input_key_material: &[ByteTarget],
) -> Vec<ByteTarget> {
    hmac_sha256(builder, salt, input_key_material, OutputSize::Full)
}

// Implementation of https://www.rfc-editor.org/rfc/rfc5869.html#section-2.3
pub fn hkdf_sha256_expand<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    pseudorandom_key: &[ByteTarget],
    info: &[ByteTarget],
    output_length_in_bytes: usize,
) -> Vec<ByteTarget> {
    let mut current_block = vec![];
    let mut current_output = vec![];
    let mut current_iteration: u8 = 0;
    while current_output.len() < output_length_in_bytes {
        current_iteration = current_iteration.wrapping_add(1);
        let iteration_byte = builder.constant_byte(current_iteration);
        current_block = hmac_sha256(
            builder,
            pseudorandom_key,
            &[&current_block, info, &[iteration_byte]].concat(),
            OutputSize::Full,
        )
        .to_vec();
        current_output.extend_from_slice(&current_block);
    }
    current_output[..output_length_in_bytes].to_vec()
}

pub fn hkdf_sha256<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    salt: &[ByteTarget],
    input_key_material: &[ByteTarget],
    info: &[ByteTarget],
    output_length_in_bytes: usize,
) -> Vec<ByteTarget> {
    let pseudorandom_key = hkdf_sha256_extract(builder, salt, input_key_material);
    hkdf_sha256_expand(builder, &pseudorandom_key, info, output_length_in_bytes)
}

#[cfg(test)]
mod test {
    use anyhow::Result;
    use plonky2::field::extension::Extendable;
    use plonky2::hash::hash_types::RichField;
    use plonky2::iop::witness::PartialWitness;
    use plonky2::plonk::circuit_builder::CircuitBuilder;
    use plonky2::plonk::circuit_data::CircuitConfig;
    use plonky2::plonk::config::GenericConfig;
    use plonky2::plonk::config::PoseidonGoldilocksConfig;
    use plonky2_bytes::ByteTarget;
    use plonky2_bytes::CircuitBuilderBytes;

    use crate::hkdf::hkdf_sha256;

    fn constant_bytes<F: RichField + Extendable<D>, const D: usize>(
        builder: &mut CircuitBuilder<F, D>,
        values: &[u8],
    ) -> Vec<ByteTarget> {
        values
            .iter()
            .map(|value| builder.constant_byte(*value))
            .collect()
    }

    struct TestVector {
        input_key_material: &'static str,
        salt: &'static str,
        info: &'static str,
        output_length_in_bytes: usize,
        result: &'static str,
    }

    #[test]
    fn test_hkdf_against_test_vectors() -> Result<()> {
        // Test vectors from https://www.rfc-editor.org/rfc/rfc5869.html#appendix-A
        let test_vectors = [
            TestVector {
                input_key_material: "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                salt: "000102030405060708090a0b0c",
                info: "f0f1f2f3f4f5f6f7f8f9",
                output_length_in_bytes: 42,
                result: "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            },
            TestVector {
                input_key_material: "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f",
                salt: "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
                info: "b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
                output_length_in_bytes: 82,
                result: "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87",
            },
            TestVector {
                input_key_material: "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                salt: "",
                info: "",
                output_length_in_bytes: 42,
                result: "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            },
        ];

        for test_vector in test_vectors {
            let input_key_material = hex::decode(test_vector.input_key_material)?;
            let salt = hex::decode(test_vector.salt)?;
            let info = hex::decode(test_vector.info)?;
            let expected_result = hex::decode(test_vector.result)?;

            const D: usize = 2;
            type C = PoseidonGoldilocksConfig;
            type F = <C as GenericConfig<D>>::F;
            let mut builder =
                CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

            let mut library_result = vec![0; test_vector.output_length_in_bytes];
            hmac_sha256::HKDF::expand(
                &mut library_result,
                hmac_sha256::HKDF::extract(&salt, &input_key_material),
                &info,
            );

            assert_eq!(library_result, expected_result);

            let input_key_material_target = constant_bytes(&mut builder, &input_key_material);
            let salt_target = constant_bytes(&mut builder, &salt);
            let info_target = constant_bytes(&mut builder, &info);
            let expected_result_target = constant_bytes(&mut builder, &expected_result);

            let result_target = hkdf_sha256(
                &mut builder,
                &salt_target,
                &input_key_material_target,
                &info_target,
                test_vector.output_length_in_bytes,
            );
            builder.connect_bytes(&expected_result_target, &result_target);

            let data = builder.build::<C>();
            let proof = data.prove(PartialWitness::new())?;
            data.verify(proof)?;
        }
        Ok(())
    }
}
