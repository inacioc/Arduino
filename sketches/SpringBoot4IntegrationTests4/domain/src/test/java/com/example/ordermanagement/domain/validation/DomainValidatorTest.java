package com.example.ordermanagement.domain.validation;

import com.example.ordermanagement.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class DomainValidatorTest {

    private final DomainValidator<List<Integer>> positiveNumbers = (numbers, notification) -> {
        for (int i = 0; i < numbers.size(); i++) {
            if (numbers.get(i) <= 0) {
                notification.addError("NOT_POSITIVE", "numbers[" + i + "]", "Must be positive.");
            }
        }
    };

    @Test
    @DisplayName("assertValid throws once, carrying every violation found")
    void assertValid_collectsAllViolations() {
        assertThatThrownBy(() -> positiveNumbers.assertValid(List.of(1, -2, 3, 0)))
                .isInstanceOfSatisfying(DomainValidationException.class, ex ->
                        assertThat(ex.getViolations())
                                .extracting(DomainViolation::code, DomainViolation::propertyPath)
                                .containsExactly(
                                        tuple("NOT_POSITIVE", "numbers[1]"),
                                        tuple("NOT_POSITIVE", "numbers[3]")))
                .hasMessage("Domain validation failed with 2 violation(s).");
    }

    @Test
    @DisplayName("assertValid does nothing when no rule is broken")
    void assertValid_noViolations_doesNotThrow() {
        assertThatCode(() -> positiveNumbers.assertValid(List.of(1, 2, 3))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("several validators can share one notification")
    void validators_shareOneNotification() {
        DomainValidator<List<Integer>> notTooLong = (numbers, notification) -> {
            if (numbers.size() > 2) {
                notification.addError("TOO_LONG", "numbers", "At most 2 numbers.");
            }
        };
        ValidationNotification notification = new ValidationNotification();

        List<Integer> input = List.of(-1, 2, 3);
        positiveNumbers.validate(input, notification);
        notTooLong.validate(input, notification);

        assertThat(notification.getErrors()).extracting(DomainViolation::code)
                .containsExactly("NOT_POSITIVE", "TOO_LONG");
    }

    @Test
    @DisplayName("two-argument addError uses the default code; the error list is read-only")
    void notification_defaultCodeAndImmutability() {
        ValidationNotification notification = new ValidationNotification();
        notification.addError("schedule.startDate", "Start date must be before end date.");

        assertThat(notification.hasErrors()).isTrue();
        assertThat(notification.getErrors()).containsExactly(
                new DomainViolation(DomainViolation.DEFAULT_CODE, "schedule.startDate", "Start date must be before end date."));
        assertThatThrownBy(() -> notification.getErrors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
