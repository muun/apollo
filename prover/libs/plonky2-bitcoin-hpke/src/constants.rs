pub const HPKE_VERSION: &[u8] = b"HPKE-v1";

pub const MODEL_BASE: usize = 0x00;
pub const DEFAULT_PSK: &[u8] = b"";
pub const DEFAULT_PSK_ID: &[u8] = b"";

pub const KEM_ID: usize = 0x0016; // DHKEM(secp256k1, HKDF-SHA256)
pub const LENGTH_SECRET_BYTES: usize = 32;
pub const LENGTH_ENCAPSULATED_BYTES: usize = 65;
pub const LENGTH_PUBLIC_KEY_BYTES: usize = 65;
pub const LENGTH_DIFFIE_HELLMAN_BYTES: usize = 32;

pub const KDF_ID: usize = 0x0001; // HKDF-SHA256

pub const AEAD_ID: usize = 0x0003; // ChaCha20Poly1305
pub const LENGTH_KEY_BYTES: usize = 32;
pub const LENGTH_NONCE_BYTES: usize = 12;
