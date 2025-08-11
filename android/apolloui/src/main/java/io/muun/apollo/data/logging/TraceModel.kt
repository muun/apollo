package io.muun.apollo.data.logging


/**
 * A TraceSection contains an exception in the causal chain, followed by the code lines that led
 * up to the throw.
 */
data class TraceSection(
    /** The cause, corresponding to an Exception */
    val cause: TraceCause,

    /** The execution trace leading up to the throw */
    val lines: MutableList<TraceLine> = mutableListOf()
) {

    override fun toString() =
        "$cause\n${lines.joinToString("\n") { "    at $it" }}"

    fun filterLines(f: (TraceLine) -> Boolean) =
        copy(lines = lines.filter(f).toMutableList())

    fun replaceLines(singleLine: TraceLine) =
        replaceLines(listOf(singleLine))

    fun replaceLines(newLines: List<TraceLine>) =
        copy(lines = newLines.toMutableList())

}


/**
 * A TraceCause corresponds to an exception in the causal chain.
 */
data class TraceCause(
    /** The fully-qualified name of the Throwable subclass */
    val className: String,

    /** The message given when the exception was constructed */
    val message: String,

    /** Only in Rx composite traces, the relative position of this cause in the assembly trace */
    val rxOrder: Int? = null
) {

    override fun toString() =
        "$className: $message${rxOrder?.let {" (composed cause $it)"} ?: ""}"
}


/**
 * A TraceLine indicates a point in code, much like a StackTraceElement.
 */
data class TraceLine(
    /** The fully-qualified name of the class where this code point lies */
    val className: String,

    /** The method in that class where this code point lies */
    val methodName: String,

    /** The filename where the class can be found */
    val fileName: String,

    /** The line number in that file where the code point lies */
    val lineNumber: Int,

    /** Only for errors thrown in Rx streams, the Rx operator called at this code point */
    val rxOperator: String? = null
) {
    override fun toString() =
        "$className.$methodName${rxOperator?.let { "[$it]" } ?: ""}($fileName:$lineNumber)"

    fun toStackTraceElement() =
        StackTraceElement(
            className,
            methodName + (rxOperator?.let { "[$it]" } ?: ""),
            fileName,
            lineNumber
        )
}


/**
 * Only in Rx composite traces, a header that indicates the relative position of a cause.
 */
data class TraceCompositeCauseHeader(
    val rxOrder: Int
)


/**
 * A Trace represents a parsed Java stack trace, with additional metadata.
 */
data class Trace(
    val sections: MutableList<TraceSection> = mutableListOf()
) {

    override fun toString() =
        sections.joinToString("\n\n")
}
