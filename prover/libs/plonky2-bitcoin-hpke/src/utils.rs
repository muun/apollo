pub fn i2osp(mut n: usize, len: usize) -> Vec<u8> {
    let mut result = vec![];
    for _ in 0..len {
        result.push((n % 256) as u8);
        n /= 256;
    }
    result.reverse();
    result
}
