mod circuit;
mod inputs;
mod interface;
mod testing;

pub use circuit::AAD;
pub use circuit::INFO;
pub use interface::Proof;
pub use interface::ProverData;
pub use interface::ProverInputs;
pub use interface::VerifierData;
pub use interface::VerifierInputs;
pub use interface::precompute;
pub use interface::prove;
pub use interface::verify;
