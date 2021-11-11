#ifndef SECP256K1_MUSIG_H
#define SECP256K1_MUSIG_H

#include "secp256k1_extrakeys.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

/** This module implements a Schnorr-based multi-signature scheme called MuSig2
 * (https://eprint.iacr.org/2020/1261). It is compatible with BIP-340 ("Schnorr").
 * There's an example C source file in the module's directory
 * (src/modules/musig/example.c) that demonstrates how it can be used.
 *
 * The module also supports BIP-341 ("Taproot") public key tweaking and adaptor
 * signatures as described in
 * https://github.com/ElementsProject/scriptless-scripts/pull/24
 *
 * It is recommended to read the documention in this include file carefully.
 * Further notes on API usage can be found in src/modules/musig/musig.md
 */

/** Opaque data structures
 *
 *  The exact representation of data inside is implementation defined and not
 *  guaranteed to be portable between different platforms or versions. It can,
 *  however, be safely copied/moved. If you need to convert to a format suitable
 *  for storage, transmission, or comparison, use the corresponding
 *  serialization and parsing functions.
 */

/** Opaque data structure that caches information about public key aggregation.
 *
 *  Guaranteed to be 165 bytes in size. No serialization and parsing functions
 *  (yet).
 */
typedef struct {
    unsigned char data[165];
} secp256k1_musig_keyagg_cache;

/** Opaque data structure that holds a signer's _secret_ nonce.
 *
 *  Guaranteed to be 68 bytes in size.
 *
 *  WARNING: This structure MUST NOT be copied or read or written to directly. A
 *  signer who is online throughout the whole process and can keep this
 *  structure in memory can use the provided API functions for a safe standard
 *  workflow. See
 *  https://blockstream.com/2019/02/18/musig-a-new-multisignature-standard/ for
 *  more details about the risks associated with serializing or deserializing
 *  this structure.
 *
 *  We repeat, copying this data structure can result in nonce reuse which will
 *  leak the secret signing key.
 */
typedef struct {
    unsigned char data[68];
} secp256k1_musig_secnonce;

/** Opaque data structure that holds a signer's public nonce.
*
*  Guaranteed to be 132 bytes in size. Serialized and parsed with
*  `musig_pubnonce_serialize` and `musig_pubnonce_parse`.
*/
typedef struct {
    unsigned char data[132];
} secp256k1_musig_pubnonce;

/** Opaque data structure that holds an aggregate public nonce.
 *
 *  Guaranteed to be 132 bytes in size. Serialized and parsed with
 *  `musig_aggnonce_serialize` and `musig_aggnonce_parse`.
 */
typedef struct {
    unsigned char data[132];
} secp256k1_musig_aggnonce;

/** Opaque data structure that holds a cache for a MuSig session.
 *
 *  This structure is not necessarily required to be kept secret. Guaranteed to
 *  be 133 bytes in size. No serialization and parsing functions (yet).
 */
typedef struct {
    unsigned char data[133];
} secp256k1_musig_session;

/** Opaque data structure that holds a partial MuSig signature.
 *
 *  Guaranteed to be 36 bytes in size. Serialized and parsed with
 *  `musig_partial_sig_serialize` and `musig_partial_sig_parse`.
 */
typedef struct {
    unsigned char data[36];
} secp256k1_musig_partial_sig;

/** Parse a signers public nonce.
 *
 *  Returns: 1 when the nonce could be parsed, 0 otherwise.
 *  Args:    ctx: a secp256k1 context object
 *  Out:   nonce: pointer to a nonce object
 *  In:     in66: pointer to the 66-byte nonce to be parsed
 */
SECP256K1_API int secp256k1_musig_pubnonce_parse(
    const secp256k1_context* ctx,
    secp256k1_musig_pubnonce* nonce,
    const unsigned char *in66
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Serialize a signer's public nonce
 *
 *  Returns: 1 when the nonce could be serialized, 0 otherwise
 *  Args:    ctx: a secp256k1 context object
 *  Out:   out32: pointer to a 66-byte array to store the serialized nonce
 *  In:    nonce: pointer to the nonce
 */
SECP256K1_API int secp256k1_musig_pubnonce_serialize(
    const secp256k1_context* ctx,
    unsigned char *out66,
    const secp256k1_musig_pubnonce* nonce
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Parse an aggregate public nonce.
 *
 *  Returns: 1 when the nonce could be parsed, 0 otherwise.
 *  Args:    ctx: a secp256k1 context object
 *  Out:   nonce: pointer to a nonce object
 *  In:     in66: pointer to the 66-byte nonce to be parsed
 */
SECP256K1_API int secp256k1_musig_aggnonce_parse(
    const secp256k1_context* ctx,
    secp256k1_musig_aggnonce* nonce,
    const unsigned char *in66
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Serialize an aggregate public nonce
 *
 *  Returns: 1 when the nonce could be serialized, 0 otherwise
 *  Args:    ctx: a secp256k1 context object
 *  Out:   out32: pointer to a 66-byte array to store the serialized nonce
 *  In:    nonce: pointer to the nonce
 */
SECP256K1_API int secp256k1_musig_aggnonce_serialize(
    const secp256k1_context* ctx,
    unsigned char *out66,
    const secp256k1_musig_aggnonce* nonce
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Serialize a MuSig partial signature or adaptor signature
 *
 *  Returns: 1 when the signature could be serialized, 0 otherwise
 *  Args:    ctx: a secp256k1 context object
 *  Out:   out32: pointer to a 32-byte array to store the serialized signature
 *  In:      sig: pointer to the signature
 */
SECP256K1_API int secp256k1_musig_partial_sig_serialize(
    const secp256k1_context* ctx,
    unsigned char *out32,
    const secp256k1_musig_partial_sig* sig
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Parse a MuSig partial signature.
 *
 *  Returns: 1 when the signature could be parsed, 0 otherwise.
 *  Args:    ctx: a secp256k1 context object
 *  Out:     sig: pointer to a signature object
 *  In:     in32: pointer to the 32-byte signature to be parsed
 *
 *  After the call, sig will always be initialized. If parsing failed or the
 *  encoded numbers are out of range, signature verification with it is
 *  guaranteed to fail for every message and public key.
 */
SECP256K1_API int secp256k1_musig_partial_sig_parse(
    const secp256k1_context* ctx,
    secp256k1_musig_partial_sig* sig,
    const unsigned char *in32
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Computes a aggregate public key and the hash of the given public keys.
 *
 *  Different orders of `pubkeys` result in different `agg_pk`s.
 *
 *  The pubkeys can be sorted before combining with `secp256k1_xonly_sort` which
 *  ensures the same resulting `agg_pk` for the same multiset of pubkeys.
 *  This is useful to do before pubkey_agg, such that the order of pubkeys
 *  does not affect the aggregate public key.
 *
 *  Returns: 1 if the public keys were successfully aggregated, 0 otherwise
 *  Args:        ctx: pointer to a context object initialized for verification
 *           scratch: scratch space used to compute the aggregate pubkey by
 *                    multiexponentiation. If NULL, an inefficient algorithm is used.
 *  Out:      agg_pk: the MuSig-aggregated xonly public key. If you do not need it,
 *                    this arg can be NULL.
 *      keyagg_cache: if non-NULL, pointer to a musig_keyagg_cache struct that
 *                    is required for signing (or verifying the MuSig protocol).
 *   In:     pubkeys: input array of pointers to public keys to aggregate. The order
 *                    is important; a different order will result in a different
 *                    aggregate public key
 *         n_pubkeys: length of pubkeys array. Must be greater than 0.
 */
SECP256K1_API int secp256k1_musig_pubkey_agg(
    const secp256k1_context* ctx,
    secp256k1_scratch_space *scratch,
    secp256k1_xonly_pubkey *agg_pk,
    secp256k1_musig_keyagg_cache *keyagg_cache,
    const secp256k1_xonly_pubkey * const* pubkeys,
    size_t n_pubkeys
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(3) SECP256K1_ARG_NONNULL(5);

/** Tweak an x-only public key by adding the generator multiplied with tweak32
 *  to it. The resulting output_pubkey with the original agg_pk output of
 *  musig_pubkey_agg and tweak passes `secp256k1_xonly_pubkey_tweak_test`.
 *
 *  This function is only useful before initializing a signing session. If you
 *  are only computing a public key, but not intending to create a signature for
 *  it, you can just use `secp256k1_xonly_pubkey_tweak_add`. Can only be called
 *  once with a given keyagg_cache.
 *
 *  Returns: 0 if the arguments are invalid or the resulting public key would be
 *           invalid (only when the tweak is the negation of the corresponding
 *           secret key) or if the key has already been tweaked. 1 otherwise.
 *  Args:            ctx: pointer to a context object initialized for verification
 *  Out:   output_pubkey: pointer to a public key to store the result. Will be set
 *                        to an invalid value if this function returns 0. If you
 *                        do not need it, this arg can be NULL.
 *               tweak32: pointer to a 32-byte tweak. If the tweak is invalid
 *                        according to secp256k1_ec_seckey_verify, this function
 *                        returns 0. For uniformly random 32-byte arrays the
 *                        chance of being invalid is negligible (around 1 in
 *                        2^128).
 *  In/Out: keyagg_cache: pointer to a `musig_keyagg_cache` struct initialized in
 *                       `musig_pubkey_agg`
 */
SECP256K1_API SECP256K1_WARN_UNUSED_RESULT int secp256k1_musig_pubkey_tweak_add(
    const secp256k1_context* ctx,
    secp256k1_pubkey *output_pubkey,
    const unsigned char *tweak32,
    secp256k1_musig_keyagg_cache *keyagg_cache
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3) SECP256K1_ARG_NONNULL(4);

/** Starts a signing session by generating a nonce
 *
 *  This function outputs a secret nonce that will be required for signing and a
 *  corresponding public nonce that is intended to be sent to other signers.
 *
 *  MuSig differs from regular Schnorr signing in that implementers _must_ take
 *  special care to not reuse a nonce. This can be ensured by following these rules:
 *
 *  1. Always provide a unique session_id32. It is a "number used once".
 *  2. If you already know the signing key, message or aggregate public key
 *     cache, they can be optionally provided to derive the nonce and increase
 *     misuse-resistance. The extra_input32 argument can be used to provide
 *     additional data that does not repeat in normal scenarios, such as the
 *     current time.
 *  3. If you do not provide a seckey, session_id32 _must_ be UNIFORMLY RANDOM.
 *     If you do provide a seckey, session_id32 can instead be a counter (that
 *     must never repeat!). However, it is recommended to always choose
 *     session_id32 uniformly at random. Note that using the same seckey for
 *     multiple MuSig sessions is fine.
 *  4. Avoid copying (or serializing) the secnonce. This reduces the possibility
 *     that it is used more than once for signing.
 *
 *  Remember that nonce reuse will immediately leak the secret key!
 *
 *  Returns: 0 if the arguments are invalid and 1 otherwise
 *  Args:         ctx: pointer to a context object, initialized for signing
 *  Out:     secnonce: pointer to a structure to store the secret nonce
 *           pubnonce: pointer to a structure to store the public nonce
 *  In:  session_id32: a 32-byte session_id32 as explained above. Must be
 *                     uniformly random unless you really know what you are
 *                     doing.
 *             seckey: the 32-byte secret key that will be used for signing if
 *                     already known (can be NULL)
 *              msg32: the 32-byte message that will be signed if already known
 *                     (can be NULL)
 *       keyagg_cache: pointer to the keyagg_cache that was used to create the aggregate
 *                     (and tweaked) public key if already known (can be NULL)
 *      extra_input32: an optional 32-byte array that is input to the nonce
 *                     derivation function (can be NULL)
 */
SECP256K1_API int secp256k1_musig_nonce_gen(
    const secp256k1_context* ctx,
    secp256k1_musig_secnonce *secnonce,
    secp256k1_musig_pubnonce *pubnonce,
    const unsigned char *session_id32,
    const unsigned char *seckey,
    const unsigned char *msg32,
    const secp256k1_musig_keyagg_cache *keyagg_cache,
    const unsigned char *extra_input32
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(4);

/** Aggregates the nonces of every signer into a single nonce
 *
 *  This can be done by an untrusted third party to reduce the communication
 *  between signers. Instead of everyone sending nonces to everyone else, there
 *  can be one party receiving all nonces, aggregating the nonces with this
 *  function and then sending only the aggregate nonce back to the signers.
 *
 *  Returns: 0 if the arguments are invalid or if all signers sent invalid
 *           pubnonces, 1 otherwise
 *  Args:                 ctx: pointer to a context object
 *  Out:       aggnonce: pointer to an the aggregate public nonce object for
 *                       musig_nonce_process
 *  In:       pubnonces: array of pointers to public nonces sent by the
 *                       signers
 *          n_pubnonces: number of elements in the pubnonces array. Must be
 *                       greater than 0.
 */
SECP256K1_API int secp256k1_musig_nonce_agg(
    const secp256k1_context* ctx,
    secp256k1_musig_aggnonce  *aggnonce,
    const secp256k1_musig_pubnonce * const* pubnonces,
    size_t n_pubnonces
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Takes the public nonces of all signers and computes a session cache that is
 *  required for signing and verification of partial signatures and a signature
 *  template that is required for combining partial signatures.
 *
 *  If the adaptor argument is non-NULL then the output of musig_partial_sig_agg
 *  will be an invalid Schnorr signature, until the signature is given to
 *  musig_adapt with the corresponding secret adaptor.
 *
 *  Returns: 0 if the arguments are invalid or if all signers sent invalid
 *           pubnonces, 1 otherwise
 *  Args:         ctx: pointer to a context object, initialized for verification
 * Out:       session: pointer to a struct to store the session
 * In:       aggnonce: pointer to an the aggregate public nonce object that is
 *                     output of musig_nonce_agg
 *              msg32: the 32-byte message to sign
 *       keyagg_cache: pointer to the keyagg_cache that was used to create the
 *                     aggregate (and tweaked) pubkey
 *            adaptor: optional pointer to an adaptor point encoded as a public
 *                     key if this signing session is part of an adaptor
 *                     signature protocol
 */
SECP256K1_API int secp256k1_musig_nonce_process(
    const secp256k1_context* ctx,
    secp256k1_musig_session *session,
    const secp256k1_musig_aggnonce  *aggnonce,
    const unsigned char *msg32,
    const secp256k1_musig_keyagg_cache *keyagg_cache,
    const secp256k1_pubkey *adaptor
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3) SECP256K1_ARG_NONNULL(4) SECP256K1_ARG_NONNULL(5);

/** Produces a partial signature
 *
 *  This function sets the given secnonce to 0 and will abort if given a
 *  secnonce that is 0. This is a best effort attempt to protect against nonce
 *  reuse. However, this is of course easily defeated if the secnonce has been
 *  copied (or serialized). Remember that nonce reuse will immediately leak the
 *  secret key!
 *
 *  Returns: 0 if the arguments are invalid or the provided secnonce has already
 *           been used for signing, 1 otherwise
 *  Args:         ctx: pointer to a context object
 *  Out:  partial_sig: pointer to struct to store the partial signature
 *  In/Out:  secnonce: pointer to the secnonce struct created in
 *                     musig_nonce_gen
 *  In:       keypair: pointer to keypair to sign the message with
 *       keyagg_cache: pointer to the keyagg_cache that was output when the
 *                     aggregate public key for this session
 *      session: pointer to the session that was created with
 *                     musig_nonce_process
 */
SECP256K1_API int secp256k1_musig_partial_sign(
    const secp256k1_context* ctx,
    secp256k1_musig_partial_sig *partial_sig,
    secp256k1_musig_secnonce *secnonce,
    const secp256k1_keypair *keypair,
    const secp256k1_musig_keyagg_cache *keyagg_cache,
    const secp256k1_musig_session *session
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3) SECP256K1_ARG_NONNULL(4) SECP256K1_ARG_NONNULL(5) SECP256K1_ARG_NONNULL(6);

/** Checks that an individual partial signature verifies
 *
 *  This function is essential when using protocols with adaptor signatures.
 *  However, it is not essential for regular MuSig's, in the sense that if any
 *  partial signatures does not verify, the full signature will also not verify, so the
 *  problem will be caught. But this function allows determining the specific party
 *  who produced an invalid signature, so that signing can be restarted without them.
 *
 *  Returns: 0 if the arguments are invalid or the partial signature does not
 *           verify
 *  Args         ctx: pointer to a context object, initialized for verification
 *  In:  partial_sig: pointer to partial signature to verify
 *          pubnonce: public nonce sent by the signer who produced the
 *                    signature
 *            pubkey: public key of the signer who produced the signature
 *      keyagg_cache: pointer to the keyagg_cache that was output when the
 *                    aggregate public key for this session
 *           session: pointer to the session that was created with
 *                    musig_nonce_process
 */
SECP256K1_API SECP256K1_WARN_UNUSED_RESULT int secp256k1_musig_partial_sig_verify(
    const secp256k1_context* ctx,
    const secp256k1_musig_partial_sig *partial_sig,
    const secp256k1_musig_pubnonce *pubnonce,
    const secp256k1_xonly_pubkey *pubkey,
    const secp256k1_musig_keyagg_cache *keyagg_cache,
    const secp256k1_musig_session *session
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3) SECP256K1_ARG_NONNULL(4) SECP256K1_ARG_NONNULL(5) SECP256K1_ARG_NONNULL(6);

/** Aggregates partial signatures
 *
 *  Returns: 0 if the arguments are invalid or a partial_sig is out of range, 1
 *           otherwise (which does NOT mean the resulting signature verifies).
 *  Args:         ctx: pointer to a context object
 *  Out:        sig64: complete Schnorr signature
 *  In:       session: pointer to the session that was created with
 *                     musig_nonce_process
 *       partial_sigs: array of pointers to partial signatures to aggregate
 *             n_sigs: number of elements in the partial_sigs array
 */
SECP256K1_API SECP256K1_WARN_UNUSED_RESULT int secp256k1_musig_partial_sig_agg(
    const secp256k1_context* ctx,
    unsigned char *sig64,
    const secp256k1_musig_session *session,
    const secp256k1_musig_partial_sig * const* partial_sigs,
    size_t n_sigs
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3) SECP256K1_ARG_NONNULL(4);

/** Extracts the nonce_parity bit from a session
 *
 *  This is used for adaptor signatures.
 *
 *  Returns: 0 if one of the arguments was NULL, and 1 otherwise.
 *  Args:         ctx: pointer to a context object
 *  Out: nonce_parity: pointer to an integer that indicates the parity
 *                     of the aggregate public nonce. Used for adaptor
 *                     signatures.
 *  In:       session: pointer to the session that was created with
 *                     musig_nonce_process
 */
int secp256k1_musig_nonce_parity(
    const secp256k1_context* ctx,
    int *nonce_parity,
    secp256k1_musig_session *session
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Converts a pre-signature that misses the adaptor into a full signature
 *
 *  If the sec_adaptor32 argument is incorrect, the adapted signature will be
 *  invalid. This function does not verify the adapted signature.
 *
 *  Returns: 1: signature and secret adaptor contained valid values (which does
 *              NOT mean the signature or the adaptor are valid!)
 *           0: otherwise
 *  Args:         ctx: pointer to a context object
 *  In/Out:     sig64: 64-byte pre-signature that is adapted to a full signature
 *  In: sec_adaptor32: 32-byte secret adaptor to add to the partial signature
 *       nonce_parity: the output of `musig_nonce_parity` called with the
 *                     session used for producing sig64
 */
SECP256K1_API int secp256k1_musig_adapt(
    const secp256k1_context* ctx,
    unsigned char *sig64,
    const unsigned char *sec_adaptor32,
    int nonce_parity
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3);

/** Extracts a secret adaptor from a MuSig pre-signature and corresponding
 *  signature
 *
 *  This function will not fail unless given grossly invalid data; if it is
 *  merely given signatures that do not verify, the returned value will be
 *  nonsense. It is therefore important that all data be verified at earlier
 *  steps of any protocol that uses this function. In particular, this includes
 *  verifying all partial signatures that were aggregated into pre_sig64.
 *
 *  Returns: 1: signatures contained valid data such that an adaptor could be
 *              extracted (which does NOT mean the signatures or the adaptor are
 *              valid!)
 *           0: otherwise
 *  Args:         ctx: pointer to a context object
 *  Out:sec_adaptor32: 32-byte secret adaptor
 *  In:         sig64: complete, valid 64-byte signature
 *          pre_sig64: the pre-signature corresponding to sig64, i.e., the
 *                     aggregate of partial signatures without the secret
 *                     adaptor
 *       nonce_parity: the output of `musig_nonce_parity` called with the
 *                     session used for producing sig64
 */
SECP256K1_API SECP256K1_WARN_UNUSED_RESULT int secp256k1_musig_extract_adaptor(
    const secp256k1_context* ctx,
    unsigned char *sec_adaptor32,
    const unsigned char *sig64,
    const unsigned char *pre_sig64,
    int nonce_parity
) SECP256K1_ARG_NONNULL(1) SECP256K1_ARG_NONNULL(2) SECP256K1_ARG_NONNULL(3) SECP256K1_ARG_NONNULL(4);

#ifdef __cplusplus
}
#endif

#endif
