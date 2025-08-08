use alloc::vec::Vec;

use plonky2::util::serialization::Buffer;
use plonky2::util::serialization::IoResult;
use plonky2::util::serialization::Read;
use plonky2::util::serialization::Write;

use crate::gadgets::arithmetic_u32::U32Target;

pub trait WriteU32 {
    fn write_target_u32(&mut self, x: U32Target) -> IoResult<()>;
}

impl WriteU32 for Vec<u8> {
    #[inline]
    fn write_target_u32(&mut self, x: U32Target) -> IoResult<()> {
        self.write_target(x.0)
    }
}

pub trait ReadU32 {
    fn read_target_u32(&mut self) -> IoResult<U32Target>;
}

impl ReadU32 for Buffer<'_> {
    #[inline]
    fn read_target_u32(&mut self) -> IoResult<U32Target> {
        Ok(U32Target(self.read_target()?))
    }
}
