#!/bin/bash

set -xe

if [[ -z "$1" ]]; then
    echo "Usage: $0 <path to lib>"
    exit 1
fi

if [[ $(dirname "$0") != "." ]]; then
    echo "Not on musig dir"
    exit 1
fi

lib_path="$1"

(
    cd "$lib_path";
    ./autogen.sh;
    ./configure --with-asm=no;
    make;
    make check
)

find . -name "*.c" -and -not -name "umbrella.c" -delete
find . -name "*.h" -and -not -name "umbrella.h" -delete
rm -f secp256k1.k

cp -r "$lib_path"/include/*.h .
cp -r "$lib_path"/src/*.c .
cp -r "$lib_path"/src/*.h .

function include_module() {
    cp -r "$lib_path"/src/modules/"$1"/*.h .
    # Modules are composed of all .h files. Some contain several headers, but
    # all have a file named main_impl.h with all the logic. To avoid modules 
    # overwritting each other files, we rename it to module_main_impl.h
    mv main_impl.h "$1_main_impl.h"
}

include_module extrakeys
include_module schnorrsig
include_module musig

# Delete unit tests, benchmarks and unused headers.
rm \
    tests.c \
    tests_exhaustive.c \
    tests_exhaustive_impl.h \
    tests_impl.h \
    valgrind_ctime_test.c \
    bench_*.c \
    bench.h \
    secp256k1_ecdh.h \
    secp256k1_ecdsa_adaptor.h \
    secp256k1_ecdsa_s2c.h \
    secp256k1_generator.h \
    secp256k1_rangeproof.h \
    secp256k1_recovery.h \
    secp256k1_surjectionproof.h \
    secp256k1_whitelist.h \
    gen_context.c 

# This file makes cgo go crazy, but we need it.
# The solution is to rename to avoid cgo compiling it, and then including it from 
# umbrella.c
mv secp256k1.c secp256k1.k

# Remove all folder references from includes, they are not needed anymore
sed -i ""  's/include \"[.\/a-z]*\/\([^.\/]*\.h\)/include "\1/g' \
    secp256k1.k \
    *.c \
    *.h

go test -v
