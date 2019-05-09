package io.muun.apollo.data.logging;

import rx.exceptions.AssemblyStackTraceException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Utility class to filter and assemble improved stack traces.
 */
public class StackTraceBuilder {

    /**
     * The Pattern to match a single stack trace line.
     */
    private static final Pattern STACK_TRACE_ELEMENT_PATTERN = Pattern
            .compile("^at (.*)\\.(.*)\\((.*):([0-9]+)\\)$");

    /**
     * A regex to match newlines in their various formats.
     */
    private static final String NEW_LINE_REGEX = "\\n\\r|\\r\\n|\\n|\\r";


    private final String include;
    private final String[] excludeAll;


    /**
     * Constructor.
     * @param include Relevant lines in a stack trace MUST begin with this string.
     * @param excludeAll Relevant lines in a stack trace MUST NOT begin with any of these strings.
     */
    public StackTraceBuilder(String include, String[] excludeAll) {
        this.include = include;
        this.excludeAll = excludeAll;
    }


    /**
     * Extract the trace from a given Throwable, filter out the noise and return a relevant trace.
     */
    public StackTraceElement[] createRelevantStackTrace(Throwable e) {
        final Set<StackTraceElement> summary = new LinkedHashSet<>();

        Throwable next = e;

        while (next != null) {
            if (next instanceof AssemblyStackTraceException) {
                // AssemblyStackTraceExceptions *have no actual stack trace*. Instead, they cram
                // everything into the message string.
                addRelevantElements(summary, next.getMessage());
            } else {
                addRelevantElements(summary, next);
            }

            next = next.getCause();
        }

        return summary.toArray(new StackTraceElement[0]);
    }


    private void addRelevantElements(Set<StackTraceElement> accum, Throwable next) {
        for (final StackTraceElement element: getStackTrace(next)) {
            if (isRelevant(element)) {
                accum.add(element);
            }
        }
    }

    private void addRelevantElements(Set<StackTraceElement> accum, String nextTrace) {
        final String[] lines = nextTrace.split(NEW_LINE_REGEX);

        for (String line: lines) {
            final StackTraceElement element = parseStackTraceLine(line.trim());

            if (element != null && isRelevant(element)) {
                accum.add(element);
            }
        }
    }

    private boolean isRelevant(StackTraceElement element) {
        final String label = element.getClassName();

        if (! label.startsWith(include)) {
            return false;
        }

        for (final String exclude: excludeAll) {
            if (label.startsWith(exclude)) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    private StackTraceElement parseStackTraceLine(String stackTraceLine) {
        final Matcher matcher = STACK_TRACE_ELEMENT_PATTERN.matcher(stackTraceLine);

        if (! matcher.matches()) {
            return null;
        }

        final String clazz = matcher.group(1);
        final String method = matcher.group(2);
        final String filename = matcher.group(3);
        final int line = Integer.valueOf(matcher.group(4));

        return new StackTraceElement(clazz, method, filename, line);
    }

    private StackTraceElement[] getStackTrace(Throwable e) {
        return (e.getStackTrace() != null) ? e.getStackTrace() : new StackTraceElement[0];
    }
}
