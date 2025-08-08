use anyhow::Result;
use plonky2::field::extension::Extendable;
use plonky2::field::types::PrimeField64;
use plonky2::hash::hash_types::RichField;
use plonky2::iop::target::BoolTarget;
use plonky2::iop::witness::Witness;
use plonky2::plonk::circuit_builder::CircuitBuilder;

#[derive(Clone, Copy, Debug)]
pub struct ByteTarget {
    pub bits: [BoolTarget; 8],
}

pub trait CircuitBuilderBytes<F: RichField + Extendable<D>, const D: usize> {
    fn constant_byte(&mut self, byte: u8) -> ByteTarget;

    fn constant_bytes(&mut self, values: &[u8]) -> Vec<ByteTarget>;

    fn connect_byte(&mut self, byte1: ByteTarget, byte2: ByteTarget);

    fn connect_bytes(&mut self, bytes1: &[ByteTarget], bytes2: &[ByteTarget]);

    fn xor(&mut self, byte1: ByteTarget, byte2: ByteTarget) -> ByteTarget;

    fn register_public_byte(&mut self, byte: &ByteTarget);

    fn register_public_bytes(&mut self, bytes: &[ByteTarget]);
}

impl<F: RichField + Extendable<D>, const D: usize> CircuitBuilderBytes<F, D>
    for CircuitBuilder<F, D>
{
    fn constant_byte(&mut self, byte: u8) -> ByteTarget {
        ByteTarget {
            bits: (0..8)
                .map(|i| self.constant_bool(ith_most_significant_bit(byte, i)))
                .collect::<Vec<_>>()
                .try_into()
                .unwrap(),
        }
    }

    fn constant_bytes(&mut self, values: &[u8]) -> Vec<ByteTarget> {
        values
            .iter()
            .map(|value| self.constant_byte(*value))
            .collect()
    }

    fn connect_byte(&mut self, byte1: ByteTarget, byte2: ByteTarget) {
        for (bit1, bit2) in byte1.bits.iter().zip(byte2.bits.iter()) {
            self.connect(bit1.target, bit2.target)
        }
    }

    fn connect_bytes(&mut self, bytes1: &[ByteTarget], bytes2: &[ByteTarget]) {
        assert_eq!(bytes1.len(), bytes2.len());
        for (byte1, byte2) in bytes1.iter().zip(bytes2.iter()) {
            self.connect_byte(*byte1, *byte2);
        }
    }

    fn xor(&mut self, byte1: ByteTarget, byte2: ByteTarget) -> ByteTarget {
        ByteTarget {
            bits: byte1
                .bits
                .iter()
                .zip(byte2.bits.iter())
                .map(|(bit1, bit2)| xor(self, *bit1, *bit2))
                .collect::<Vec<_>>()
                .try_into()
                .unwrap(),
        }
    }

    fn register_public_byte(&mut self, byte: &ByteTarget) {
        byte.bits
            .iter()
            .for_each(|bit| self.register_public_input(bit.target))
    }
    fn register_public_bytes(&mut self, bytes: &[ByteTarget]) {
        bytes
            .iter()
            .for_each(|&byte| self.register_public_byte(&byte));
    }
}

pub fn bytes_from_bits(bits: &[BoolTarget]) -> Vec<ByteTarget> {
    assert_eq!(bits.len() % 8, 0);
    bits.chunks_exact(8)
        .map(|bits| ByteTarget {
            bits: bits.try_into().unwrap(),
        })
        .collect()
}

pub fn ith_most_significant_bit(byte: u8, bit_index: usize) -> bool {
    ((byte >> (7 - bit_index)) & 1) != 0
}

pub fn xor<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    bit1: BoolTarget,
    bit2: BoolTarget,
) -> BoolTarget {
    let s = builder.sub(bit1.target, bit2.target);
    BoolTarget::new_unsafe(builder.mul(s, s))
}

pub trait WitnessBytes<F: PrimeField64>: Witness<F> {
    fn set_byte(&mut self, byte: &ByteTarget, value: u8) -> Result<()>;

    fn set_bytes(&mut self, targets: &[ByteTarget], values: &[u8]) -> Result<()>;
}

impl<T: Witness<F>, F: PrimeField64> WitnessBytes<F> for T {
    fn set_byte(&mut self, target: &ByteTarget, value: u8) -> Result<()> {
        for (i, bool_target) in target.bits.iter().enumerate() {
            self.set_bool_target(*bool_target, ith_most_significant_bit(value, i))?
        }

        Ok(())
    }
    fn set_bytes(&mut self, targets: &[ByteTarget], values: &[u8]) -> Result<()> {
        assert_eq!(targets.len(), values.len());

        for (i, target) in targets.iter().enumerate() {
            self.set_byte(target, values[i])?
        }

        Ok(())
    }
}
