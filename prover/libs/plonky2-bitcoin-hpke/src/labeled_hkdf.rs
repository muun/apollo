use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_bytes::ByteTarget;
use plonky2_bytes::CircuitBuilderBytes;
use plonky2_hkdf_sha256::hkdf_sha256_expand;
use plonky2_hkdf_sha256::hkdf_sha256_extract;

use crate::constants::AEAD_ID;
use crate::constants::HPKE_VERSION;
use crate::constants::KDF_ID;
use crate::constants::KEM_ID;
use crate::utils::i2osp;

fn suite_id_for_kem() -> Vec<u8> {
    [b"KEM" as &[u8], &i2osp(KEM_ID, 2)].concat()
}

fn suite_id_for_hpke() -> Vec<u8> {
    [
        b"HPKE" as &[u8],
        &i2osp(KEM_ID, 2),
        &i2osp(KDF_ID, 2),
        &i2osp(AEAD_ID, 2),
    ]
    .concat()
}

fn suite_id(usage: Usage) -> Vec<u8> {
    match usage {
        Usage::Kem => suite_id_for_kem(),
        Usage::Hpke => suite_id_for_hpke(),
    }
}
pub enum Usage {
    Kem,
    Hpke,
}

pub fn labeled_extract<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    salt: &[ByteTarget],
    label: &[u8],
    key_material: &[ByteTarget],
    usage: Usage,
) -> Vec<ByteTarget> {
    let label = builder.constant_bytes(label);
    let tag = builder.constant_bytes(HPKE_VERSION);
    let suite_id = builder.constant_bytes(&suite_id(usage));
    let labeled_ikm = [&tag as &[_], &suite_id, &label, key_material].concat();
    hkdf_sha256_extract(builder, salt, &labeled_ikm)
}

pub fn labeled_expand<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    pseudorandom_key: &[ByteTarget],
    label: &[u8],
    info: &[ByteTarget],
    length_in_bytes: usize,
    usage: Usage,
) -> Vec<ByteTarget> {
    let label = builder.constant_bytes(label);
    let length_string = builder.constant_bytes(&i2osp(length_in_bytes, 2));
    let tag = builder.constant_bytes(HPKE_VERSION);
    let suite_id = builder.constant_bytes(&suite_id(usage));
    let info = [&length_string as &[_], &tag, &suite_id, &label, info].concat();
    hkdf_sha256_expand(builder, pseudorandom_key, &info, length_in_bytes)
}
