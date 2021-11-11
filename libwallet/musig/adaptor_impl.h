/**********************************************************************
 * Copyright (c) 2021 Jonas Nick                                      *
 * Distributed under the MIT software license, see the accompanying   *
 * file COPYING or http://www.opensource.org/licenses/mit-license.php.*
 **********************************************************************/

#ifndef _SECP256K1_MODULE_MUSIG_ADAPTOR_IMPL_
#define _SECP256K1_MODULE_MUSIG_ADAPTOR_IMPL_

#include "session.h"

int secp256k1_musig_nonce_parity(const secp256k1_context* ctx, int *nonce_parity, secp256k1_musig_session *session) {
    secp256k1_musig_session_internal session_i;
    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(nonce_parity != NULL);
    ARG_CHECK(session != NULL);

    if (!secp256k1_musig_session_load(ctx, &session_i, session)) {
        return 0;
    }
    *nonce_parity = session_i.fin_nonce_parity;
    return 1;
}

int secp256k1_musig_adapt(const secp256k1_context* ctx, unsigned char *sig64, const unsigned char *sec_adaptor32, int nonce_parity) {
    secp256k1_scalar s;
    secp256k1_scalar t;
    int overflow;

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(sig64 != NULL);
    ARG_CHECK(sec_adaptor32 != NULL);

    secp256k1_scalar_set_b32(&s, &sig64[32], &overflow);
    if (overflow) {
        return 0;
    }
    secp256k1_scalar_set_b32(&t, sec_adaptor32, &overflow);
    if (overflow) {
        secp256k1_scalar_clear(&t);
        return 0;
    }

    if (nonce_parity) {
        secp256k1_scalar_negate(&t, &t);
    }

    secp256k1_scalar_add(&s, &s, &t);
    secp256k1_scalar_get_b32(&sig64[32], &s);
    secp256k1_scalar_clear(&t);
    return 1;
}

int secp256k1_musig_extract_adaptor(const secp256k1_context* ctx, unsigned char *sec_adaptor32, const unsigned char *sig64, const unsigned char *pre_sig64, int nonce_parity) {
    secp256k1_scalar t;
    secp256k1_scalar s;
    int overflow;

    VERIFY_CHECK(ctx != NULL);
    ARG_CHECK(sec_adaptor32 != NULL);
    ARG_CHECK(sig64 != NULL);
    ARG_CHECK(pre_sig64 != NULL);

    secp256k1_scalar_set_b32(&t, &sig64[32], &overflow);
    if (overflow) {
        return 0;
    }
    secp256k1_scalar_negate(&t, &t);

    secp256k1_scalar_set_b32(&s, &pre_sig64[32], &overflow);
    if (overflow) {
        return 0;
    }
    secp256k1_scalar_add(&t, &t, &s);

    if (!nonce_parity) {
        secp256k1_scalar_negate(&t, &t);
    }
    secp256k1_scalar_get_b32(sec_adaptor32, &t);
    secp256k1_scalar_clear(&t);
    return 1;
}

#endif
