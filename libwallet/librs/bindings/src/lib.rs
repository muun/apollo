use std::any::Any;
use std::panic;
use std::slice;

use anyhow::anyhow;
use cosigning_key_validation::Proof;
use cosigning_key_validation::VerifierData;
use cosigning_key_validation::VerifierInputs;

#[repr(C)]
pub struct CharArray {
    data: *const libc::c_char,
    len: u64,
}

unsafe fn c_array_to_vec(arr: CharArray) -> Vec<u8> {
    unsafe { slice::from_raw_parts(arr.data.cast(), arr.len as usize) }.to_vec()
}

fn vec_to_dangling_c_arr(vec: &[u8]) -> CharArray {
    let ptr = unsafe { libc::malloc(vec.len()) };
    unsafe {
        libc::memcpy(ptr, vec.as_ptr().cast(), vec.len());
    }
    CharArray {
        data: ptr.cast_const().cast(),
        len: vec.len() as u64,
    }
}

fn to_fixed_size_arr<const N: usize>(v: &[u8]) -> anyhow::Result<[u8; N]> {
    v.try_into()
        .map_err(|_| anyhow!("expected length {}, got {}", N, v.len()))
}

#[cfg(feature = "precomputed_verifier_data")]
fn verifier_data() -> &'static [u8] {
    include_bytes!("bin/verifier_data.bin")
}

#[cfg(not(feature = "precomputed_verifier_data"))]
fn verifier_data() -> &'static [u8] {
    panic!("no verifier data")
}

pub fn get_panic_message(panic: &Box<dyn Any + Send>) -> &str {
    panic
        .downcast_ref::<String>()
        .map(String::as_str)
        .or_else(|| panic.downcast_ref::<&'static str>().copied())
        .unwrap_or("unknown panic")
}

#[unsafe(no_mangle)]
pub extern "C" fn plonky2_server_key_verify(
    proof: CharArray,
    recovery_code_public_key: CharArray,
    hpke_ephemeral_public_key: CharArray,
    ciphertext: CharArray,
    plaintext_public_key: CharArray,
) -> CharArray {
    // Set an empty panic hook to prevent printing to stderr
    panic::set_hook(Box::new(|_info| {}));

    let res = panic::catch_unwind(|| -> anyhow::Result<()> {
        let proof = unsafe { c_array_to_vec(proof) };
        let recovery_code_public_key = unsafe { c_array_to_vec(recovery_code_public_key) };
        let hpke_ephemeral_public_key = unsafe { c_array_to_vec(hpke_ephemeral_public_key) };
        let ciphertext = unsafe { c_array_to_vec(ciphertext) };
        let plaintext_public_key = unsafe { c_array_to_vec(plaintext_public_key) };

        cosigning_key_validation::verify(
            &VerifierData::deserialize(verifier_data())?,
            Proof(proof.to_vec()),
            &VerifierInputs {
                hpke_ephemeral_public_key: to_fixed_size_arr(&hpke_ephemeral_public_key)?,
                recovery_code_public_key: to_fixed_size_arr(&recovery_code_public_key)?,
                plaintext_public_key: to_fixed_size_arr(&plaintext_public_key)?,
                ciphertext: to_fixed_size_arr(&ciphertext)?,
            },
        )?;

        Ok(())
    });

    let output = match res {
        Ok(Ok(())) => "ok".to_string(),
        Ok(Err(e)) => format!("error: {}", e),
        Err(e) => format!("panic: {}", get_panic_message(&e)),
    };
    vec_to_dangling_c_arr(output.as_bytes())
}
