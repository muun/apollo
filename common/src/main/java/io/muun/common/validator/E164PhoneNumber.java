package io.muun.common.validator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;

/**
 * Validates E.164 phone number.
 *
 * <p>The number must contain the plus sign at the beginning.
 */
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = E164PhoneNumber.Validator.class)
public @interface E164PhoneNumber {

    /**
     * The error message to show.
     */
    String message() default "must be valid E.164 phone number (including plus sign)";

    /**
     * Ignored, required by Hibernate Validator.
     */
    Class<?>[] groups() default {};

    /**
     * Ignored, required by Hibernate Validator.
     */
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<E164PhoneNumber, String> {

        private final Pattern validationPattern = Pattern.compile("^\\+[1-9]\\d{1,14}$");

        @Override
        public void initialize(E164PhoneNumber constraintAnnotation) {
        }

        @Override
        public boolean isValid(String string,
                               ConstraintValidatorContext constraintValidatorContext) {
            if (string == null) {
                return false;
            }

            final Matcher matcher = validationPattern.matcher(string);
            return matcher.matches();
        }
    }
}