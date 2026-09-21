package com.example.ordermanagement.domain.validation;

import com.example.ordermanagement.domain.exception.DomainValidationException;

/**
 * A functional rule set for one kind of object. Reports every broken rule into the
 * notification rather than throwing, so several validators can run over the same target
 * and the caller sees all violations at once.
 * <p>
 * A validator that needs data it doesn't own (e.g. "does this product exist?") takes the
 * driven port through its constructor - the domain still depends only on its own ports.
 */
@FunctionalInterface
public interface DomainValidator<T> {

    void validate(T target, ValidationNotification notification);

    /** Runs this validator alone and throws {@link DomainValidationException} if it found anything. */
    default void assertValid(T target) {
        ValidationNotification notification = new ValidationNotification();
        validate(target, notification);
        if (notification.hasErrors()) {
            throw new DomainValidationException(notification.getErrors());
        }
    }
}
