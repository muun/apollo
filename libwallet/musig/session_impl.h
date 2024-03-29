/**********************************************************************
 * Copyright (c) 2021 Jonas Nick                                      *
 * Distributed under the MIT software license, see the accompanying   *
 * file COPYING or http://www.opensource.org/licenses/mit-license.php.*
 **********************************************************************/

#ifndef _SECP256K1_MODULE_MUSIG_SESSION_IMPL_
#define _SECP256K1_MODULE_MUSIG_SESSION_IMPL_

#include "keyagg.h"
#include "session.h"

static const unsigned char secp256k1_musig_secnonce_magic[4] = { 0x22, 0x0e, 0xdc, 0xf1 };

static void secp256k1_musig_secnonce_save(secp256k1_musig_secnonce *secnonce, secp256k1_scalar *k) {
    memcpy(&secnonce->data[0], secp256k1_musig_secnonce_magic, 4);
    secp256k1_scalar_get_b32(&secnonce->data[4], &k[0]);
    secp256k1_scalar_get_b32(&secnonce->data[36], &k[1]);
}

static int secp256k1_musig_secnonce_load(const secp256k1_context* ctx, secp256k1_scalar *k, secp256k1_musig_secnonce *secnonce) {
    ARG_CHECK(secp256k1_memcmp_var(&secnonce->data[0], secp256k1_musig_secnonce_magic, 4) == 0);
    secp256k1_scalar_set_b32(&k[0], &secnonce->data[4], NULL);
    secp256k1_scalar_set_b32(&k[1], &secnonce->data[36], NULL);
    return 1;
}

static const unsigned char secp256k1_musig_pubnonce_magic[4] = { 0xf5, 0x7a, 0x3d, 0xa0 };

/* Requires that none of the provided group elements is infinity. Works for both
 * musig_pubnonce and musig_aggnonce. */
static void secp256k1_musig_pubnonce_save(secp256k1_musig_pubnonce* nonce, secp256k1_ge* ge) {
    int i;
    memcpy(&nonce->data[0], secp256k1_musig_pubnonce_magic, 4);
    for (i = 0; i < 2; i++) {
        secp256k1_point_save(nonce->data + 4+64*i, &ge[i]);
    }
}

/* Works for both musig_pubnonce and musig_aggnonce. Returns 1 unless the nonce
 * wasn't properly initialized */
static int secp256k1_musig_pubnonce_load(const secp256k1_context* ctx, secp256k1_ge* ge, const secp256k1_musig_pubnonce* nonce) {
    int i;

    ARG_CHECK(secp256k1_memcmp_var(&nonce->data[0], secp256k1_musig_pubnonce_magic, 4) == 0);
    for (i = 0; i < 2; i++) {
        secp256k1_point_load(&ge[i], nonce->data + 4 + 64*i);
    }
    return 1;
}

static const unsigned char secp256k1_musig_session_cache_magic[4] = { 0x9d, 0xed, 0xe9, 0x17 };

/* A session consists of
 * - 4 byte session cache
 * - 1 byte the parity of the aggregate nonce
 * - 32 byte aggregated nonce
 * - 32 byte nonce aggregation coefficient b
 * - 32 byte signature challenge hash e
 * - 32 byte scalar s that is added to the partial signatures of the signers
 */
static void secp256k1_musig_session_save(secp256k1_musig_session *session, const secp256k1_musig_session_internal *session_i) {
    unsigned char *ptr = session->data;

    memcpy(ptr, secp256k1_musig_session_cache_magic, 4);
    ptr += 4;
    *ptr = session_i->fin_nonce_parity;
    ptr += 1;
    memmove(ptr, session_i->fin_nonce, 32);
    ptr += 32;
    secp256k1_scalar_get_b32(ptr, &session_i->noncecoef);
    ptr += 32;
    secp256k1_scalar_get_b32(ptr, &session_i->challenge);
    ptr += 32;
    secp256k1_scalar_get_b32(ptr, &session_i->s_part);
}

static int secp256k1_musig_session_load(const secp256k1_context* ctx, secp256k1_musig_session_internal *session_i, const secp256k1_musig_session *session) {
    const unsigned char *ptr = session->data;

    ARG_CHECK(secp256k1_memcmp_var(ptr, secp256k1_musig_session_cache_magic, 4) == 0);
    ptr += 4;
    session_i->fin_nonce_parity = *ptr;
    ptr += 1;
    session_i->fin_nonce = ptr;
    ptr += 32;
    secp256k1_scalar_set_b32(&session_i->noncecoef, ptr, NULL);
    ptr += 32;
    secp256k1_scalar_set_b32(&session_i->challenge, ptr, NULL);
    ptr += 32;
    secp256k1_scalar_set_b32(&session_i->s_part, ptr, NULL);
    return 1;
}

static const unsigned char secp256k1_musig_partial_sig_magic[4] = { 0xeb, 0xfb, 0x1a, 0x32 };

static void secp256k1_musig_partial_sig_save(secp256k1_musig_partial_sig* sig, secp256k1_scalar *s) {
    memcpy(&sig->data[0], secp256k1_musig_partial_sig_magic, 4);
    secp256k1_scalar_get_b32(&sig->data[4], s);
}

static int secp256k1_musig_partial_sig_load(const secp256k1_context* ctx, secp256k1_scalar *s, const secp256k1_musig_partial_sig* sig) {
    int overflow;

    ARG_CHECK(secp256k1_memcmp_var(&sig->data[0], secp256k1_musig_partial_sig_magic, 4) == 0);
    secp256k1_scalar_set_b32(s, &sig->data[4], &overflow);
    /* Parsed signatures can not overflow */
    VERIFY_CHECK(!overflow);
    return 1;
}

int secp256k1_musig_pubnonce_serialize(const secp256k1_context* ctx, unsigned char *out66, const secp256k1_musig_pubnonce* nonce) {
    secp256k1_ge ge[2];
    int i;

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(out66 != NULL);
    memset(out66, 0, 66);
    ARG_CHECK(nonce != NULL);

    if (!secp256k1_musig_pubnonce_load(ctx, ge, nonce)) {
        return 0;
    }
    for (i = 0; i < 2; i++) {
        int ret;
        size_t size = 33;
        ret = secp256k1_eckey_pubkey_serialize(&ge[i], &out66[33*i], &size, 1);
        /* serialize must succeed because the point was just loaded */
        VERIFY_CHECK(ret);
    }
    return 1;
}

int secp256k1_musig_pubnonce_parse(const secp256k1_context* ctx, secp256k1_musig_pubnonce* nonce, const unsigned char *in66) {
    secp256k1_ge ge[2];
    int i;

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(nonce != NULL);
    ARG_CHECK(in66 != NULL);

    for (i = 0; i < 2; i++) {
        if (!secp256k1_eckey_pubkey_parse(&ge[i], &in66[33*i], 33)) {
            return 0;
        }
        if (!secp256k1_ge_is_in_correct_subgroup(&ge[i])) {
            return 0;
        }
    }
    /* The group elements can not be infinity because they were just parsed */
    secp256k1_musig_pubnonce_save(nonce, ge);
    secp256k1_ge_clear(&ge[0]);
    secp256k1_ge_clear(&ge[1]);
    return 1;
}

int secp256k1_musig_aggnonce_serialize(const secp256k1_context* ctx, unsigned char *out66, const secp256k1_musig_aggnonce* nonce) {
    return secp256k1_musig_pubnonce_serialize(ctx, out66, (secp256k1_musig_pubnonce*) nonce);
}

int secp256k1_musig_aggnonce_parse(const secp256k1_context* ctx, secp256k1_musig_aggnonce* nonce, const unsigned char *in66) {
    return secp256k1_musig_pubnonce_parse(ctx, (secp256k1_musig_pubnonce*) nonce, in66);
}

int secp256k1_musig_partial_sig_serialize(const secp256k1_context* ctx, unsigned char *out32, const secp256k1_musig_partial_sig* sig) {
    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(out32 != NULL);
    ARG_CHECK(sig != NULL);
    memcpy(out32, &sig->data[4], 32);
    return 1;
}

int secp256k1_musig_partial_sig_parse(const secp256k1_context* ctx, secp256k1_musig_partial_sig* sig, const unsigned char *in32) {
    secp256k1_scalar tmp;
    int overflow;
    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(sig != NULL);
    ARG_CHECK(in32 != NULL);

    secp256k1_scalar_set_b32(&tmp, in32, &overflow);
    if (overflow) {
        secp256k1_scalar_clear(&tmp);
        return 0;
    }
    secp256k1_musig_partial_sig_save(sig, &tmp);
    secp256k1_scalar_clear(&tmp);
    return 1;
}

/* Normalizes the x-coordinate of the given group element. */
static int secp256k1_xonly_ge_serialize(unsigned char *output32, secp256k1_ge *ge) {
    if (secp256k1_ge_is_infinity(ge)) {
        return 0;
    }
    secp256k1_fe_normalize_var(&ge->x);
    secp256k1_fe_get_b32(output32, &ge->x);
    return 1;
}

static void secp256k1_nonce_function_musig(secp256k1_scalar *k, const unsigned char *session_id, const unsigned char *key32, const unsigned char *msg32, const unsigned char *agg_pk, const unsigned char *extra_input32) {
    secp256k1_sha256 sha;
    unsigned char seed[32];
    unsigned char i;
    enum { n_extra_in = 4 };
    const unsigned char *extra_in[n_extra_in];

    /* TODO: this doesn't have the same sidechannel resistance as the BIP340
     * nonce function because the seckey feeds directly into SHA. */
    secp256k1_sha256_initialize_tagged(&sha, (unsigned char*)"MuSig/nonce", 11);
    secp256k1_sha256_write(&sha, session_id, 32);
    extra_in[0] = key32;
    extra_in[1] = agg_pk;
    extra_in[2] = msg32;
    extra_in[3] = extra_input32;
    for (i = 0; i < n_extra_in; i++) {
        unsigned char marker;
        if (extra_in[i] != NULL) {
            marker = 1;
            secp256k1_sha256_write(&sha, &marker, 1);
            secp256k1_sha256_write(&sha, extra_in[i], 32);
        } else {
            marker = 0;
            secp256k1_sha256_write(&sha, &marker, 1);
        }
    }
    secp256k1_sha256_finalize(&sha, seed);

    for (i = 0; i < 2; i++) {
        unsigned char buf[32];
        secp256k1_sha256_initialize(&sha);
        secp256k1_sha256_write(&sha, seed, 32);
        secp256k1_sha256_write(&sha, &i, 1);
        secp256k1_sha256_finalize(&sha, buf);
        secp256k1_scalar_set_b32(&k[i], buf, NULL);
    }
}

int secp256k1_musig_nonce_gen(const secp256k1_context* ctx, secp256k1_musig_secnonce *secnonce, secp256k1_musig_pubnonce *pubnonce, const unsigned char *session_id32, const unsigned char *seckey, const unsigned char *msg32, const secp256k1_musig_keyagg_cache *keyagg_cache, const unsigned char *extra_input32) {
    secp256k1_keyagg_cache_internal cache_i;
    secp256k1_scalar k[2];
    secp256k1_ge nonce_pt[2];
    int i;
    unsigned char pk_ser[32];
    unsigned char *pk_ser_ptr = NULL;

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(secnonce != NULL);
    memset(secnonce, 0, sizeof(*secnonce));
    ARG_CHECK(pubnonce != NULL);
    memset(pubnonce, 0, sizeof(*pubnonce));
    ARG_CHECK(session_id32 != NULL);
    ARG_CHECK(secp256k1_ecmult_gen_context_is_built(&ctx->ecmult_gen_ctx));

    /* Check that the seckey is valid to be able to sign for it later. */
    if (seckey != NULL) {
        secp256k1_scalar sk;
        int ret;
        ret = secp256k1_scalar_set_b32_seckey(&sk, seckey);
        /* The declassified return value indicates the validity of the seckey.
         * If this function is called correctly it is always 1. (Note:
         * declassify was only required for valgrind_ctime_test build with
         * USE_ASM_X86_64=no. */
        secp256k1_declassify(ctx, &ret, sizeof(ret));
        ARG_CHECK(ret);
        secp256k1_scalar_clear(&sk);
    }

    if (keyagg_cache != NULL) {
        int ret;
        if (!secp256k1_keyagg_cache_load(ctx, &cache_i, keyagg_cache)) {
            return 0;
        }
        ret = secp256k1_xonly_ge_serialize(pk_ser, &cache_i.pk);
        /* Serialization can not fail because the loaded point can not be infinity. */
        VERIFY_CHECK(ret);
        pk_ser_ptr = pk_ser;
    }
    secp256k1_nonce_function_musig(k, session_id32, seckey, msg32, pk_ser_ptr, extra_input32);
    VERIFY_CHECK(!secp256k1_scalar_is_zero(&k[0]));
    VERIFY_CHECK(!secp256k1_scalar_is_zero(&k[1]));
    secp256k1_musig_secnonce_save(secnonce, k);

    for (i = 0; i < 2; i++) {
        secp256k1_gej nonce_ptj;
        secp256k1_ecmult_gen(&ctx->ecmult_gen_ctx, &nonce_ptj, &k[i]);
        secp256k1_ge_set_gej(&nonce_pt[i], &nonce_ptj);
        secp256k1_declassify(ctx, &nonce_pt[i], sizeof(nonce_pt));
        secp256k1_scalar_clear(&k[i]);
    }
    /* nonce_pt can't be infinity because k != 0 */
    secp256k1_musig_pubnonce_save(pubnonce, nonce_pt);
    return 1;
}

static int secp256k1_musig_sum_nonces(const secp256k1_context* ctx, secp256k1_gej *summed_nonces, const secp256k1_musig_pubnonce * const* pubnonces, size_t n_pubnonces) {
    size_t i;
    int j;

    secp256k1_gej_set_infinity(&summed_nonces[0]);
    secp256k1_gej_set_infinity(&summed_nonces[1]);

    for (i = 0; i < n_pubnonces; i++) {
        secp256k1_ge nonce_pt[2];
        if (!secp256k1_musig_pubnonce_load(ctx, nonce_pt, pubnonces[i])) {
            return 0;
        }
        for (j = 0; j < 2; j++) {
            secp256k1_gej_add_ge_var(&summed_nonces[j], &summed_nonces[j], &nonce_pt[j], NULL);
        }
    }
    return 1;
}

int secp256k1_musig_nonce_agg(const secp256k1_context* ctx, secp256k1_musig_aggnonce  *aggnonce, const secp256k1_musig_pubnonce * const* pubnonces, size_t n_pubnonces) {
    secp256k1_gej aggnonce_ptj[2];
    secp256k1_ge aggnonce_pt[2];
    int i;

    ARG_CHECK(aggnonce != NULL);
    ARG_CHECK(pubnonces != NULL);
    ARG_CHECK(n_pubnonces > 0);

    if (!secp256k1_musig_sum_nonces(ctx, aggnonce_ptj, pubnonces, n_pubnonces)) {
        return 0;
    }
    for (i = 0; i < 2; i++) {
        if (secp256k1_gej_is_infinity(&aggnonce_ptj[i])) {
            return 0;
        }
        secp256k1_ge_set_gej(&aggnonce_pt[i], &aggnonce_ptj[i]);
    }
    secp256k1_musig_pubnonce_save((secp256k1_musig_pubnonce*)aggnonce, aggnonce_pt);
    return 1;
}

/* hash(aggnonce[0], aggnonce[1], agg_pk, msg) */
static int secp256k1_musig_compute_noncehash(unsigned char *noncehash, secp256k1_ge *aggnonce, const unsigned char *agg_pk32, const unsigned char *msg) {
    unsigned char buf[33];
    secp256k1_sha256 sha;
    int i;

    secp256k1_sha256_initialize(&sha);
    for (i = 0; i < 2; i++) {
        size_t size = sizeof(buf);
        if (!secp256k1_eckey_pubkey_serialize(&aggnonce[i], buf, &size, 1)) {
            return 0;
        }
        secp256k1_sha256_write(&sha, buf, sizeof(buf));
    }
    secp256k1_sha256_write(&sha, agg_pk32, 32);
    secp256k1_sha256_write(&sha, msg, 32);
    secp256k1_sha256_finalize(&sha, noncehash);
    return 1;
}

static int secp256k1_musig_nonce_process_internal(const secp256k1_ecmult_context* ecmult_ctx, int *fin_nonce_parity, unsigned char *fin_nonce, secp256k1_scalar *b, secp256k1_gej *aggnoncej, const unsigned char *agg_pk32, const unsigned char *msg) {
    unsigned char noncehash[32];
    secp256k1_ge fin_nonce_pt;
    secp256k1_gej fin_nonce_ptj;
    secp256k1_ge aggnonce[2];

    secp256k1_ge_set_gej(&aggnonce[0], &aggnoncej[0]);
    secp256k1_ge_set_gej(&aggnonce[1], &aggnoncej[1]);
    if (!secp256k1_musig_compute_noncehash(noncehash, aggnonce, agg_pk32, msg)) {
        return 0;
    }
    /* aggnonce = aggnonces[0] + b*aggnonces[1] */
    secp256k1_scalar_set_b32(b, noncehash, NULL);
    secp256k1_ecmult(ecmult_ctx, &fin_nonce_ptj, &aggnoncej[1], b, NULL);
    secp256k1_gej_add_ge(&fin_nonce_ptj, &fin_nonce_ptj, &aggnonce[0]);
    secp256k1_ge_set_gej(&fin_nonce_pt, &fin_nonce_ptj);
    if (!secp256k1_xonly_ge_serialize(fin_nonce, &fin_nonce_pt)) {
        /* unreachable with overwhelming probability */
        return 0;
    }
    secp256k1_fe_normalize_var(&fin_nonce_pt.y);
    *fin_nonce_parity = secp256k1_fe_is_odd(&fin_nonce_pt.y);
    return 1;
}

int secp256k1_musig_nonce_process(const secp256k1_context* ctx, secp256k1_musig_session *session, const secp256k1_musig_aggnonce  *aggnonce, const unsigned char *msg32, const secp256k1_musig_keyagg_cache *keyagg_cache, const secp256k1_pubkey *adaptor) {
    secp256k1_keyagg_cache_internal cache_i;
    secp256k1_ge aggnonce_pt[2];
    secp256k1_gej aggnonce_ptj[2];
    unsigned char fin_nonce[32];
    secp256k1_musig_session_internal session_i;
    unsigned char agg_pk32[32];

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(secp256k1_ecmult_context_is_built(&ctx->ecmult_ctx));
    ARG_CHECK(session != NULL);
    ARG_CHECK(aggnonce != NULL);
    ARG_CHECK(msg32 != NULL);
    ARG_CHECK(keyagg_cache != NULL);

    if (!secp256k1_keyagg_cache_load(ctx, &cache_i, keyagg_cache)) {
        return 0;
    }
    secp256k1_fe_get_b32(agg_pk32, &cache_i.pk.x);

    if (!secp256k1_musig_pubnonce_load(ctx, aggnonce_pt, (secp256k1_musig_pubnonce*)aggnonce)) {
        return 0;
    }
    secp256k1_gej_set_ge(&aggnonce_ptj[0], &aggnonce_pt[0]);
    secp256k1_gej_set_ge(&aggnonce_ptj[1], &aggnonce_pt[1]);
    /* Add public adaptor to nonce */
    if (adaptor != NULL) {
        secp256k1_ge adaptorp;
        if (!secp256k1_pubkey_load(ctx, &adaptorp, adaptor)) {
            return 0;
        }
        secp256k1_gej_add_ge_var(&aggnonce_ptj[0], &aggnonce_ptj[0], &adaptorp, NULL);
    }
    if (!secp256k1_musig_nonce_process_internal(&ctx->ecmult_ctx, &session_i.fin_nonce_parity, fin_nonce, &session_i.noncecoef, aggnonce_ptj, agg_pk32, msg32)) {
        return 0;
    }

    /* Compute messagehash and store in session cache */
    secp256k1_schnorrsig_challenge(&session_i.challenge, fin_nonce, msg32, 32, agg_pk32);

    /* If there is a tweak then set `msghash` times `tweak` to the `s`-part.*/
    secp256k1_scalar_clear(&session_i.s_part);
    if (cache_i.is_tweaked) {
        secp256k1_scalar e_tmp = session_i.challenge;
        if (!secp256k1_eckey_privkey_tweak_mul(&e_tmp, &cache_i.tweak)) {
            /* This mimics the behavior of secp256k1_ec_seckey_tweak_mul regarding
             * tweak being 0. */
            return 0;
        }
        if (secp256k1_fe_is_odd(&cache_i.pk.y)) {
            secp256k1_scalar_negate(&e_tmp, &e_tmp);
        }
        secp256k1_scalar_add(&session_i.s_part, &session_i.s_part, &e_tmp);
    }
    session_i.fin_nonce = fin_nonce;
    secp256k1_musig_session_save(session, &session_i);
    return 1;
}

int secp256k1_musig_partial_sign(const secp256k1_context* ctx, secp256k1_musig_partial_sig *partial_sig, secp256k1_musig_secnonce *secnonce, const secp256k1_keypair *keypair, const secp256k1_musig_keyagg_cache *keyagg_cache, const secp256k1_musig_session *session) {
    secp256k1_scalar sk;
    secp256k1_ge pk;
    secp256k1_scalar k[2];
    secp256k1_scalar mu, s;
    secp256k1_keyagg_cache_internal cache_i;
    secp256k1_musig_session_internal session_i;
    int ret;

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(partial_sig != NULL);
    ARG_CHECK(secnonce != NULL);
    ARG_CHECK(keypair != NULL);
    ARG_CHECK(keyagg_cache != NULL);
    ARG_CHECK(session != NULL);

    /* Fails if the magic doesn't match */
    ret = secp256k1_musig_secnonce_load(ctx, k, secnonce);
    /* Set nonce to zero to avoid nonce reuse. This will cause subsequent calls
     * of this function to fail */
    memset(secnonce, 0, sizeof(*secnonce));
    if (!ret) {
        return 0;
    }

    /* Obtain the signer's public key point and determine if the sk is
     * negated before signing. That happens if if the signer's pubkey has an odd
     * Y coordinate XOR the MuSig-aggregate pubkey has an odd Y coordinate XOR
     * (if tweaked) the internal key has an odd Y coordinate.
     *
     * This can be seen by looking at the sk key belonging to `agg_pk`.
     * Let's define
     * P' := mu_0*|P_0| + ... + mu_n*|P_n| where P_i is the i-th public key
     * point x_i*G, mu_i is the i-th KeyAgg coefficient and |.| is a function
     * that normalizes a point to an even Y by negating if necessary similar to
     * secp256k1_extrakeys_ge_even_y. Then we have
     * P := |P'| + t*G where t is the tweak.
     * And the aggregate xonly public key is
     * |P| = x*G
     *      where x = sum_i(b_i*mu_i*x_i) + b'*t
     *            b' = -1 if P != |P|, 1 otherwise
     *            b_i = -1 if (P_i != |P_i| XOR P' != |P'| XOR P != |P|) and 1
     *                otherwise.
     */
    if (!secp256k1_keypair_load(ctx, &sk, &pk, keypair)) {
        return 0;
    }
    if (!secp256k1_keyagg_cache_load(ctx, &cache_i, keyagg_cache)) {
        return 0;
    }
    secp256k1_fe_normalize_var(&pk.y);
    if((secp256k1_fe_is_odd(&pk.y)
            + secp256k1_fe_is_odd(&cache_i.pk.y)
            + (cache_i.is_tweaked
                && cache_i.internal_key_parity))
            % 2 == 1) {
        secp256k1_scalar_negate(&sk, &sk);
    }

    /* Multiply KeyAgg coefficient */
    secp256k1_fe_normalize_var(&pk.x);
    secp256k1_musig_keyaggcoef(&mu, &cache_i, &pk.x);
    secp256k1_scalar_mul(&sk, &sk, &mu);

    if (!secp256k1_musig_session_load(ctx, &session_i, session)) {
        return 0;
    }
    if (session_i.fin_nonce_parity) {
        secp256k1_scalar_negate(&k[0], &k[0]);
        secp256k1_scalar_negate(&k[1], &k[1]);
    }

    /* Sign */
    secp256k1_scalar_mul(&s, &session_i.challenge, &sk);
    secp256k1_scalar_mul(&k[1], &session_i.noncecoef, &k[1]);
    secp256k1_scalar_add(&k[0], &k[0], &k[1]);
    secp256k1_scalar_add(&s, &s, &k[0]);
    secp256k1_musig_partial_sig_save(partial_sig, &s);
    secp256k1_scalar_clear(&sk);
    secp256k1_scalar_clear(&k[0]);
    secp256k1_scalar_clear(&k[1]);
    return 1;
}

int secp256k1_musig_partial_sig_verify(const secp256k1_context* ctx, const secp256k1_musig_partial_sig *partial_sig, const secp256k1_musig_pubnonce *pubnonce, const secp256k1_xonly_pubkey *pubkey, const secp256k1_musig_keyagg_cache *keyagg_cache, const secp256k1_musig_session *session) {
    secp256k1_keyagg_cache_internal cache_i;
    secp256k1_musig_session_internal session_i;
    secp256k1_scalar mu, e, s;
    secp256k1_gej pkj;
    secp256k1_ge nonce_pt[2];
    secp256k1_gej rj;
    secp256k1_gej tmp;
    secp256k1_ge pkp;
    int i;

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(secp256k1_ecmult_context_is_built(&ctx->ecmult_ctx));
    ARG_CHECK(partial_sig != NULL);
    ARG_CHECK(pubnonce != NULL);
    ARG_CHECK(pubkey != NULL);
    ARG_CHECK(keyagg_cache != NULL);
    ARG_CHECK(session != NULL);

    if (!secp256k1_musig_session_load(ctx, &session_i, session)) {
        return 0;
    }

    /* Compute "effective" nonce rj = aggnonce[0] + b*aggnonce[1] */
    /* TODO: use multiexp */
    for (i = 0; i < 2; i++) {
        if (!secp256k1_musig_pubnonce_load(ctx, nonce_pt, pubnonce)) {
            return 0;
        }
    }
    secp256k1_gej_set_ge(&rj, &nonce_pt[1]);
    secp256k1_ecmult(&ctx->ecmult_ctx, &rj, &rj, &session_i.noncecoef, NULL);
    secp256k1_gej_add_ge_var(&rj, &rj, &nonce_pt[0], NULL);

    if (!secp256k1_xonly_pubkey_load(ctx, &pkp, pubkey)) {
        return 0;
    }
    if (!secp256k1_keyagg_cache_load(ctx, &cache_i, keyagg_cache)) {
        return 0;
    }
    /* Multiplying the messagehash by the KeyAgg coefficient is equivalent
     * to multiplying the signer's public key by the coefficient, except
     * much easier to do. */
    secp256k1_musig_keyaggcoef(&mu, &cache_i, &pkp.x);
    secp256k1_scalar_mul(&e, &session_i.challenge, &mu);

    /* If the MuSig-aggregate point has an odd Y coordinate, the signers will
     * sign for the negation of their individual xonly public key such that the
     * aggregate signature is valid for the MuSig aggregated xonly key. If the
     * MuSig-aggregate point was tweaked then `e` is negated if the aggregate key
     * has an odd Y coordinate XOR the internal key has an odd Y coordinate.*/
    if (secp256k1_fe_is_odd(&cache_i.pk.y)
            != (cache_i.is_tweaked
                && cache_i.internal_key_parity)) {
        secp256k1_scalar_negate(&e, &e);
    }

    if (!secp256k1_musig_partial_sig_load(ctx, &s, partial_sig)) {
        return 0;
    }
    /* Compute -s*G + e*pkj + rj */
    secp256k1_scalar_negate(&s, &s);
    secp256k1_gej_set_ge(&pkj, &pkp);
    secp256k1_ecmult(&ctx->ecmult_ctx, &tmp, &pkj, &e, &s);
    if (session_i.fin_nonce_parity) {
        secp256k1_gej_neg(&rj, &rj);
    }
    secp256k1_gej_add_var(&tmp, &tmp, &rj, NULL);

    return secp256k1_gej_is_infinity(&tmp);
}

int secp256k1_musig_partial_sig_agg(const secp256k1_context* ctx, unsigned char *sig64, const secp256k1_musig_session *session, const secp256k1_musig_partial_sig * const* partial_sigs, size_t n_sigs) {
    size_t i;
    secp256k1_musig_session_internal session_i;

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(sig64 != NULL);
    ARG_CHECK(session != NULL);
    ARG_CHECK(partial_sigs != NULL);

    if (!secp256k1_musig_session_load(ctx, &session_i, session)) {
        return 0;
    }
    for (i = 0; i < n_sigs; i++) {
        secp256k1_scalar term;
        if (!secp256k1_musig_partial_sig_load(ctx, &term, partial_sigs[i])) {
            return 0;
        }
        secp256k1_scalar_add(&session_i.s_part, &session_i.s_part, &term);
    }
    secp256k1_scalar_get_b32(&sig64[32], &session_i.s_part);
    memcpy(&sig64[0], session_i.fin_nonce, 32);
    return 1;
}

#endif
