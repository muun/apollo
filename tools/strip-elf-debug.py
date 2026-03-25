#!/usr/bin/env python3
"""
Zeroes out .debug_* ELF sections in all .so files found under the given directories.

Go's compiler generates non-deterministic DWARF string tables that don't affect
compiled code but cause binary diffs between otherwise identical builds.
Zeroing these sections before comparing allows a clean diff.

Usage: strip-elf-debug.py <dir1> [dir2 ...]
"""

import sys
import os
import struct


def strip_elf_debug(filename):
    with open(filename, 'r+b') as f:
        data = bytearray(f.read())

    if data[:4] != b'\x7fELF':
        return

    ei_class = data[4]  # 1=32-bit, 2=64-bit
    endian = '<' if data[5] == 1 else '>'

    if ei_class == 2:
        e_shoff = struct.unpack_from(endian + 'Q', data, 40)[0]
        e_shentsize, e_shnum, e_shstrndx = struct.unpack_from(endian + 'HHH', data, 58)

        def get_sh(i):
            b = e_shoff + i * e_shentsize
            return (struct.unpack_from(endian + 'I', data, b)[0],
                    struct.unpack_from(endian + 'Q', data, b + 24)[0],
                    struct.unpack_from(endian + 'Q', data, b + 32)[0])
    else:
        e_shoff = struct.unpack_from(endian + 'I', data, 32)[0]
        e_shentsize, e_shnum, e_shstrndx = struct.unpack_from(endian + 'HHH', data, 46)

        def get_sh(i):
            b = e_shoff + i * e_shentsize
            return (struct.unpack_from(endian + 'I', data, b)[0],
                    struct.unpack_from(endian + 'I', data, b + 16)[0],
                    struct.unpack_from(endian + 'I', data, b + 20)[0])

    _, strtab_off, strtab_size = get_sh(e_shstrndx)
    strtab = data[strtab_off:strtab_off + strtab_size]

    for i in range(e_shnum):
        sh_name, sh_off, sh_size = get_sh(i)
        name = strtab[sh_name:strtab.find(b'\x00', sh_name)].decode('utf-8', errors='replace')
        if name.startswith('.debug') and sh_off > 0 and sh_size > 0:
            data[sh_off:sh_off + sh_size] = b'\x00' * sh_size

    with open(filename, 'wb') as f:
        f.write(data)


for root_dir in sys.argv[1:]:
    for dirpath, _, files in os.walk(root_dir):
        for fname in files:
            if fname.endswith('.so'):
                try:
                    strip_elf_debug(os.path.join(dirpath, fname))
                except Exception:
                    pass
