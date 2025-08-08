pub mod biguint;
pub mod curve;
pub mod curve_fixed_base;
pub mod curve_msm;
pub mod curve_windowed_mul;
pub mod ecdsa;
pub mod glv;
pub mod nonnative;
pub mod split_nonnative;

pub(crate) const fn ceil_div_usize(a: usize, b: usize) -> usize {
    a.div_ceil(b)
}
