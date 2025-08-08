package io.muun.apollo.data.logging

import java.io.BufferedReader
import java.io.StringReader
import java.util.regex.Pattern


/**
 * A TraceParser extracts a Trace from the raw text of a Java stack trace, with support for Rx
 * assembly and composite exceptions.
 */
class TraceParser {

    fun parse(rawStackTrace: String): Trace {
        val reader = LineReader(rawStackTrace)
        val trace = Trace()

        while (true) {
            val line = reader.next() ?: break
            val next = TraceExpr.parse(line)

            when (next) {
                is TraceCause ->
                    trace.sections.add(TraceSection(next))

                is TraceLine ->
                    trace.sections.last().lines.add(next)

                is TraceCompositeCauseHeader -> {
                    val nextLine = reader.next()!!

                    TraceExpr.parse(nextLine).let {
                        check(it is TraceCause)
                        trace.sections.add(TraceSection(it.copy(rxOrder = next.rxOrder)))
                    }
                }
            }
        }

        return trace
    }
}

object TraceExpr {

    /**
     * \p{} and \P{} constructs support Unicode properties and blocks, as well as special "Java"
     * character properties. You can access the methods in {@link java.lang.Character} class
     * e.g #isJavaIdentifierStart and #isJavaIdentifierPart.
     */
    val ID = "[\\p{javaJavaIdentifierStart}\\-][\\p{javaJavaIdentifierPart}\\$\\-]*"
    val FQ_ID = "$ID(\\.$ID)+"

    val MESSAGE = ".*"

    val FILE_NAME = ".*?"
    val LINE_NUMBER = "[0-9]+"
    val LOCATION = "($FILE_NAME):?($LINE_NUMBER)?" // note "(lambda)" has no line number

    val TRACE_CAUSE_HEADER = "^ComposedException (\\d+) :"
    val TRACE_CAUSE = "^(Caused by: )?($FQ_ID):?($MESSAGE)"
    val TRACE_LINE = "^at ($FQ_ID)\\.($ID)\\($LOCATION\\)$"

    val parseTraceCause = Pattern.compile(TRACE_CAUSE).toParser {
        TraceCause(it[2], it[4])
    }

    val parseTraceLine = Pattern.compile(TRACE_LINE).toParser {
        TraceLine(it[1], it[3], it[4], (it.getOrNull(5) ?: "0").toInt())
    }

    val parseTraceCauseHeader = Pattern.compile(TRACE_CAUSE_HEADER).toParser {
        TraceCompositeCauseHeader(it[1].toInt())
    }

    fun parse(line: String) =
        parseTraceLine(line)
            ?: parseTraceCause(line)
            ?: parseTraceCauseHeader(line)
}

/** From a regex Pattern, create a function `f(text) -> T` that parses text using the Pattern and
 *  runs the matched groups through a given `createModel(groups) -> T` function.
 */
private fun <T> Pattern.toParser(createModel: (List<String>) -> T) =
    { src: String ->
        val m = this.matcher(src)

        if (m.matches()) {
            (0..m.groupCount()).map { m.group(it) }.let(createModel)
        } else {
            null
        }
    }

class LineReader(src: String) {
    val br = BufferedReader(StringReader(src))

    fun next() =
        br.readLine()?.trim()
}
