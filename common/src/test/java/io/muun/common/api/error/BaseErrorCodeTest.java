package io.muun.common.api.error;

import com.google.common.collect.Range;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseErrorCodeTest {

    @Test
    public void testIntersectingRanges() {

        try (BaseErrorCode.InternalState.TestSession ignored = BaseErrorCode._STATE.startTest()) {

            BaseErrorCode.registerErrorCodes(A.class, Range.closed(5, 10));
            BaseErrorCode.registerErrorCodes(B.class, Range.closed(7, 15));

            Assertions.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);

        } catch (IllegalArgumentException error) {

            assertThat(error.getMessage()).isEqualTo(String.format(
                    "Error codes in range [7, 10] are reserved by both classes:\n* %s\n* %s",
                    A.class.getCanonicalName(),
                    B.class.getCanonicalName()
            ));
        }
    }

    @Test
    public void testNonIntersectingRanges() {

        try (BaseErrorCode.InternalState.TestSession ignored = BaseErrorCode._STATE.startTest()) {

            BaseErrorCode.registerErrorCodes(A.class, Range.closed(5, 10));
            BaseErrorCode.registerErrorCodes(B.class, Range.closed(11, 15));

            assertThat(BaseErrorCode.fromValue(7)).isEqualTo(A.TERRIBLE_ERROR);
            assertThat(BaseErrorCode.fromValue(12)).isEqualTo(B.SOMETHING_HAPPENED_ERROR);
        }
    }

    @Test
    public void testCodeOutsideRange() {

        try (BaseErrorCode.InternalState.TestSession ignored = BaseErrorCode._STATE.startTest()) {

            BaseErrorCode.registerErrorCodes(A.class, Range.closed(8, 10));

            Assertions.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);

        } catch (IllegalArgumentException error) {

            assertThat(error.getMessage()).isEqualTo(String.format(
                    "Using error code 7 outside of reserved error code ranges for class %s",
                    A.class.getCanonicalName()
            ));
        }
    }

    @Test
    public void testCodeInsideRange() {

        try (BaseErrorCode.InternalState.TestSession ignored = BaseErrorCode._STATE.startTest()) {

            BaseErrorCode.registerErrorCodes(A.class, Range.closed(5, 10));

            assertThat(BaseErrorCode.fromValue(7)).isEqualTo(A.TERRIBLE_ERROR);
        }
    }

    @Test
    public void testCodeReuse() {

        try (BaseErrorCode.InternalState.TestSession ignored = BaseErrorCode._STATE.startTest()) {

            BaseErrorCode.registerErrorCodes(A.class, Range.closed(5, 10));
            BaseErrorCode.registerErrorCodes(A.class, Range.closed(11, 15));

            Assertions.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);

        } catch (IllegalArgumentException error) {

            assertThat(error.getMessage()).isEqualTo(String.format(
                    "Error code 7 is used twice in class %s",
                    A.class.getCanonicalName()
            ));
        }
    }

    private enum A implements BaseErrorCode {
        TERRIBLE_ERROR(7, StatusCode.CLIENT_FAILURE, "what did we do?!");

        private final int code;
        private final StatusCode status;
        private final String description;

        A(int code, StatusCode status, String description) {
            this.code = code;
            this.status = status;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public StatusCode getStatus() {
            return status;
        }

        public String getDescription() {
            return description;
        }
    }

    private enum B implements BaseErrorCode {
        SOMETHING_HAPPENED_ERROR(12, StatusCode.CLIENT_FAILURE, "not sure what");

        private final int code;
        private final StatusCode status;
        private final String description;

        B(int code, StatusCode status, String description) {
            this.code = code;
            this.status = status;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public StatusCode getStatus() {
            return status;
        }

        public String getDescription() {
            return description;
        }
    }
}