use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_bytes::ByteTarget;
use plonky2_bytes::CircuitBuilderBytes;

use crate::sha256_bytes::sha256_bytes;

fn pad_with_zeros<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    array: &[ByteTarget],
    target_length: usize,
) -> Vec<ByteTarget> {
    assert!(array.len() <= target_length);
    let mut array: Vec<_> = array.into();
    while array.len() < target_length {
        array.push(builder.constant_byte(0));
    }
    array
}

// From https://datatracker.ietf.org/doc/html/rfc4868#section-2.1
const BLOCK_SIZE_IN_BYTES: usize = 64;

#[derive(Clone, Copy)]
pub enum OutputSize {
    Full,

    // Truncated output mode according to https://www.rfc-editor.org/rfc/rfc2104.html#section-5
    // only used in tests (for compatibility with test vectors)
    #[allow(dead_code)]
    Truncated {
        length_in_bytes: usize,
    },
}

// Implementation of https://www.rfc-editor.org/rfc/rfc2104.html#section-2
pub fn hmac_sha256<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    key: &[ByteTarget],
    message: &[ByteTarget],
    output_size: OutputSize,
) -> Vec<ByteTarget> {
    let key = if key.len() > BLOCK_SIZE_IN_BYTES {
        sha256_bytes(builder, key).to_vec()
    } else {
        key.to_vec()
    };
    let key = pad_with_zeros(builder, &key, BLOCK_SIZE_IN_BYTES);

    let inner_padding_byte = builder.constant_byte(0x36);
    let inner_xorred_key: Vec<_> = key
        .iter()
        .map(|byte| builder.xor(*byte, inner_padding_byte))
        .collect();

    let outer_padding_byte = builder.constant_byte(0x5c);
    let outer_xorred_key: Vec<_> = key
        .iter()
        .map(|byte| builder.xor(*byte, outer_padding_byte))
        .collect();

    let intermediate_hash = sha256_bytes(builder, &[&inner_xorred_key, message].concat());
    let full_result = sha256_bytes(
        builder,
        &[&outer_xorred_key[..], &intermediate_hash].concat(),
    );
    match output_size {
        OutputSize::Full => full_result.to_vec(),
        OutputSize::Truncated { length_in_bytes } => full_result[..length_in_bytes].to_vec(),
    }
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

    use crate::hmac::OutputSize;
    use crate::hmac::hmac_sha256;

    fn constant_bytes<F: RichField + Extendable<D>, const D: usize>(
        builder: &mut CircuitBuilder<F, D>,
        values: &[u8],
    ) -> Vec<ByteTarget> {
        values
            .iter()
            .map(|value| builder.constant_byte(*value))
            .collect()
    }

    #[derive(Clone)]
    struct TestVector {
        key: &'static str,
        message: &'static str,
        result: &'static str,
        output_size: OutputSize,
    }

    #[test]
    fn test_hmac_against_test_vectors() -> Result<()> {
        // Test vector from https://datatracker.ietf.org/doc/html/rfc4231#section-4
        let test_vectors = [
            TestVector {
                key: "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                message: "4869205468657265",
                result: "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
                output_size: OutputSize::Full,
            },
            TestVector {
                key: "4a656665",
                message: "7768617420646f2079612077616e7420666f72206e6f7468696e673f",
                result: "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                output_size: OutputSize::Full,
            },
            TestVector {
                key: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                message: "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                result: "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe",
                output_size: OutputSize::Full,
            },
            TestVector {
                key: "0102030405060708090a0b0c0d0e0f10111213141516171819",
                message: "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd",
                result: "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b",
                output_size: OutputSize::Full,
            },
            TestVector {
                key: "0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c",
                message: "546573742057697468205472756e636174696f6e",
                result: "a3b6167473100ee06e0c796c2955552b",
                output_size: OutputSize::Truncated {
                    length_in_bytes: 16,
                },
            },
            TestVector {
                key: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                message: "54657374205573696e67204c6172676572205468616e20426c6f636b2d53697a65204b6579202d2048617368204b6579204669727374",
                result: "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
                output_size: OutputSize::Full,
            },
            TestVector {
                key: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                message: "5468697320697320612074657374207573696e672061206c6172676572207468616e20626c6f636b2d73697a65206b657920616e642061206c6172676572207468616e20626c6f636b2d73697a6520646174612e20546865206b6579206e6565647320746f20626520686173686564206265666f7265206265696e6720757365642062792074686520484d414320616c676f726974686d2e",
                result: "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2",
                output_size: OutputSize::Full,
            },
        ];

        for test_vector in test_vectors {
            const D: usize = 2;
            type C = PoseidonGoldilocksConfig;
            type F = <C as GenericConfig<D>>::F;
            let mut builder =
                CircuitBuilder::<F, D>::new(CircuitConfig::standard_recursion_config());

            let key = hex::decode(test_vector.key)?;
            let message = hex::decode(test_vector.message)?;
            let expected_result = hex::decode(test_vector.result)?;

            let mut h = hmac_sha256::HMAC::new(&key);
            h.update(&message);
            let library_result = match test_vector.output_size {
                OutputSize::Full => h.finalize().to_vec(),
                OutputSize::Truncated { length_in_bytes } => {
                    h.finalize()[..length_in_bytes].to_vec()
                }
            };

            assert_eq!(library_result, expected_result);

            let key_target = constant_bytes(&mut builder, &key);
            let message_target = constant_bytes(&mut builder, &message);
            let expected_result_target = constant_bytes(&mut builder, &expected_result);

            let result_target = hmac_sha256(
                &mut builder,
                &key_target,
                &message_target,
                test_vector.output_size,
            );
            builder.connect_bytes(&expected_result_target, &result_target);

            let data = builder.build::<C>();
            let proof = data.prove(PartialWitness::new())?;
            data.verify(proof)?;
        }

        Ok(())
    }
}
