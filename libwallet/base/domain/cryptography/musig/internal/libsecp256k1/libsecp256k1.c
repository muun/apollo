/* cgo compiles the .c files that sit in the package directory and ignores
 * subdirectories, so this file pulls in the upstream translation units. Quoted
 * includes resolve relative to the file holding the directive, which is what
 * lets the tree under upstream/ stay byte for byte what import.sh copied. */

#include "upstream/src/secp256k1.c"
#include "upstream/src/precomputed_ecmult.c"
#include "upstream/src/precomputed_ecmult_gen.c"
