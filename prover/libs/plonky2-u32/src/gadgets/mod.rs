pub mod arithmetic_u32;
pub mod multiple_comparison;
pub mod range_check;

pub(crate) const fn ceil_div_usize(a: usize, b: usize) -> usize {
    a.div_ceil(b)
}
