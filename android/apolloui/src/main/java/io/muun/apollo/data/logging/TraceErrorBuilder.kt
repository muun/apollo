package io.muun.apollo.data.logging

import io.muun.Cause

class TraceErrorBuilder {

    fun build(trace: Trace): Throwable =
        trace.sections.fold(null, this::toError) ?: Throwable()

    private fun toError(prevError: Throwable?, section: TraceSection) =
        Cause(section, prevError)
}