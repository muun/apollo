package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "No unused invoices are left"

class NoInvoicesLeftError : MuunError(msg)
