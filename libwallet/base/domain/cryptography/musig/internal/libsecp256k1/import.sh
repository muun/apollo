#!/bin/bash
#
# Imports the libsecp256k1 sources that this package binds to, into upstream/.
#
# The tree is copied verbatim: cgo compiles the .c files that sit in this
# directory, and those pull the upstream translation units in from upstream/,
# so nothing under upstream/ needs to be rewritten to build. Only the files
# that are never compiled are pruned.
#
# Usage: ./import.sh [ref]

set -euo pipefail

# secp256k1-zkp rather than bitcoin-core/secp256k1: its musig module is the same
# BIP327 implementation plus adaptor signatures, which upstream does not have.
# The fork publishes no releases, so the ref is a commit.
ref="${1:-10366dbbbfeb11457f2aae3b23e154ab7d6a1fe4}"
repo="https://github.com/BlockstreamResearch/secp256k1-zkp"

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
upstream="$dir/upstream"

checkout="$(mktemp -d)"
trap 'rm -rf "$checkout"' EXIT

# Fetching the ref rather than cloning a branch, so that it can be a commit.
git init --quiet "$checkout"
git -C "$checkout" remote add origin "$repo"
git -C "$checkout" fetch --quiet --depth 1 origin "$ref"
git -C "$checkout" checkout --quiet FETCH_HEAD

commit="$(git -C "$checkout" rev-parse HEAD)"

rm -rf "$upstream"
mkdir -p "$upstream"
cp -R "$checkout/include" "$checkout/src" "$upstream"

# Modules whose ENABLE_MODULE_* define is not set by libsecp256k1.go are never
# compiled in.
for module in "$upstream"/src/modules/*; do
    case "$(basename "$module")" in
        extrakeys | schnorrsig | musig) ;;
        *) rm -rf "$module" ;;
    esac
done

for header in "$upstream"/include/*.h; do
    case "$(basename "$header")" in
        secp256k1.h | secp256k1_preallocated.h) ;;
        secp256k1_extrakeys.h | secp256k1_schnorrsig.h | secp256k1_musig.h) ;;
        *) rm -f "$header" ;;
    esac
done

# Assembly is unused: the bindings build the portable C variants, so that the
# same sources compile for every gomobile and taproot4j target.
rm -rf "$upstream/src/asm"

# Tests, test vectors, benchmarks, the generators for the precomputed tables and
# the upstream build system are not part of the library itself. Neither are the
# scalar backend the exhaustive tests use, nor the commitments only the pruned
# ecdsa modules need.
rm -rf "$upstream/src/wycheproof"

find "$upstream/src" \
    \( -name 'tests*' \
    -o -name 'testutil.h' \
    -o -name 'testrand*' \
    -o -name 'unit_test.*' \
    -o -name 'vectors.h' \
    -o -name 'bench*' \
    -o -name 'ctime_tests.c' \
    -o -name 'precompute_*' \
    -o -name '*compute_table*' \
    -o -name 'scalar_low*' \
    -o -name 'eccommit.h' \
    -o -name 'CMakeLists.txt' \
    -o -name 'Makefile.am.include' \) \
    -delete

cat > "$upstream/VERSION" <<EOF
$repo
$ref
$commit
EOF

echo "imported $ref ($commit)"
