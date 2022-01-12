package io.muun.apollo.domain.errors

import newop.State

class NewOpStateError(actual: Class<out State>?, expected: Class<out State>) :
    MuunError("Unexpected state. Actual: ${actual?.simpleName} Expected: ${expected.simpleName}")