package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.MuunError
import newop.State

class NewOpStateError(actual: Class<out State>?, expected: Class<out State>) :
    MuunError("Unexpected state. Actual: ${actual?.simpleName} Expected: ${expected.simpleName}")