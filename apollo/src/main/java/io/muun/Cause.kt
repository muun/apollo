package io.muun

import io.muun.apollo.data.logging.TraceSection

class Cause(section: TraceSection, chainTo: Throwable?):
    Throwable("${section.cause.className.split(".").last()}: ${section.cause.message}", chainTo) {

    init {
        stackTrace = section.lines.map { it.toStackTraceElement() }.toTypedArray()
    }

}