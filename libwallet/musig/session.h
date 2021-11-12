/**********************************************************************
 * Copyright (c) 2021 Jonas Nick                                      *
 * Distributed under the MIT software license, see the accompanying   *
 * file COPYING or http://www.opensource.org/licenses/mit-license.php.*
 **********************************************************************/

#ifndef _SECP256K1_MODULE_MUSIG_SESSION_
#define _SECP256K1_MODULE_MUSIG_SESSION_

typedef struct {
    int fin_nonce_parity;
    const unsigned char *fin_nonce;
    secp256k1_scalar noncecoef;
    secp256k1_scalar challenge;
    secp256k1_scalar s_part;
} secp256k1_musig_session_internal;

static int secp256k1_musig_session_load(const secp256k1_context* ctx, secp256k1_musig_session_internal *session_i, const secp256k1_musig_session *session);

#endif
