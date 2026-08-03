package io.muun.apollo.domain.errors

/**
 * Classification of errors for analytics and reporting purposes.
 * - [EXPECTED]: Errors anticipated from user input or external factors (e.g., insufficient funds,
 *               invalid address).
 *               These don't require investigation in general.
 * - [UNEXPECTED]: Every error that is NOT caused by user input or expected external factors.
 *                 These may be bugs or conditions not under user control (e.g., network
 *                 connectivity, mempool state, etc.).
 *
 * All errors are sent to both Analytics and Crashlytics. The classification is included
 * as a parameter (`error_classification`) to allow filtering on the analytics backend.
 */
enum class ErrorClassification(val trackingValue: String) {
    EXPECTED("expected"),
    UNEXPECTED("unexpected")
}

/**
 * Interface for error types that provide their own classification.
 *
 * MuunError implements this interface as abstract, forcing every concrete
 * error subclass to explicitly declare its classification.
 */
interface ClassifiedError {
    val classification: ErrorClassification
}
