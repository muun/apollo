package io.muun.apollo.data.logging


private val IGNORE_RX_OPERATORS = setOf("unsafeCreate", "unsafeSubscribe", "subscribe")


// TODO try to better render emitted values by using reflection on the OnNextValue error
// TOOD handle singles and completables

/**
 * A TraceTransformer can summarize information in lengthy stack traces into a readable, rich format
 * for human eyes.
 *
 * @param includeAny a list of package names that are always relevant.
 * @param excludeAll a list of package names that are never relevant.
 */
class TraceTransformer(val includeAny: List<String>, val excludeAll: List<String>)  {

    /** Take a Trace and filter, summarize and transform it into a better trace. */
    fun transform(original: Trace): Trace {
        val transformed = Trace()
        var isAfterCompositeCause = false

        for (section in original.sections) {
            // Each section in the trace corresponds to a cause in the original Java trace. By
            // looking at sections, their contents, order and relation, we can produce a much
            // improved trace with readable data and less noise.

            if (isDuplicateSection(section)) {
                // Duplicate sections only indicate that a stack trace contains repeated references
                // to errors, and provide zero additional information.
                continue

            } else if (isEmittedValueSection(section)) {
                // Emitted value sections appear in Rx stack traces, and contain the `toString()`
                // of the value that was passing through the stream when the error occured.
                transformed.sections.add(section.filterLines { isApplicationLine(it) })

            } else if (isAssemblySection(section)) {
                // Assembly sections contain information about how an Rx the stream was constructed.
                // Looking at them, we can deduce the sequence of operators applied and their
                // locations in code. Note that this may NOT match the actual execution flow once
                // the stream is subscribed to, which can depend on the values and errors emitted.

                // Each assembly section reveals a single operator applied to the stream, and
                // whether it was called from inside another operator (eg flatMap -> lift). From
                // these sections, we'll extract a single stack trace line containing the operator
                // applied and the line in our codebase where that call can be found.
                val line = createRelevantLineFromAssemblySection(section) ?: continue
                val previousSection = transformed.sections.lastOrNull()

                // Since we transformed this entire section into a single line, and the exception
                // message itself has no information, we'll collapse all consecutive assembly
                // sections into one that contains a single line per original section.
                if (previousSection != null && shouldCollapseAssemblyInto(previousSection)) {
                    previousSection.lines.add(line)
                } else {
                    transformed.sections.add(section.replaceLines(line))
                }

            } else if (isCompositeSection(section)) {
                // This is an Rx CompositeException section. It has no relevant information itself,
                // but once found, it signals that everything that comes after this was produced
                // by Rx and will need additional noise filtering, because some errors will appear
                // more than once.
                isAfterCompositeCause = true

            } else if (!isAfterCompositeCause || section.cause.rxOrder != null) {
                // This section is a regular Java stack trace cause, it does not fall into any
                // of the special categories handled above, and has passed our noise filter (it
                // was either found outside an Rx composite trace, or inside but tagged as relevant
                // by a special Rx header we parsed).

                // We'll add the section to the transformed trace keeping only the relevant lines.
                transformed.sections.add(section.filterLines { isApplicationLine(it) || isRxOperatorLine(it) })
            }
        }

        return transformed
    }

    private fun isCompositeSection(section: TraceSection) =
        section.cause.className == rx.exceptions.CompositeException::class.java.name

    private fun isAssemblySection(section: TraceSection) =
        section.cause.className == rx.exceptions.AssemblyStackTraceException::class.java.name

    private fun isEmittedValueSection(section: TraceSection) =
        section.cause.className == rx.exceptions.OnErrorThrowable.OnNextValue::class.java.name

    private fun isDuplicateSection(traceSection: TraceSection) =
        traceSection.cause.message.contains("Duplicate found in causal chain so cropping to prevent loop")

    private fun shouldCollapseAssemblyInto(section: TraceSection) =
        isEmittedValueSection(section) || isAssemblySection(section)

    private fun isRxOperatorLine(line: TraceLine) =
        if (line.className == "rx.Observable")
            !IGNORE_RX_OPERATORS.contains(line.methodName)
        else
            false

    private fun isApplicationLine(traceLine: TraceLine) =
        includeAny.any { traceLine.className.startsWith(it) }
            && !excludeAll.any { traceLine.className.startsWith(it) }

    private fun createRelevantLineFromAssemblySection(section: TraceSection): TraceLine? {
        // Assembly sections usually contain one relevant piece of information, followed by a lot
        // of noise. By looking at them, we can deduce where in our code an Rx operator was invoked,
        // when that operator belonged to the stream that threw the original error.

        // To learn this magical datum, we have to look at the items and find the earliest point
        // where a line in our own code transitions into a line in Rx operator code. Merging those
        // two, we can produce an informative TraceLine we actually care about.
        val lines = section.lines

        for (i in 1 until lines.size) {
            val line = lines[i]
            val previousLine = lines[i - 1]

            if (isApplicationLine(line) && isRxOperatorLine(previousLine)) {
                return line.copy(rxOperator = previousLine.methodName)
            }
        }

        return null // nothing relevant in this section
    }
}

