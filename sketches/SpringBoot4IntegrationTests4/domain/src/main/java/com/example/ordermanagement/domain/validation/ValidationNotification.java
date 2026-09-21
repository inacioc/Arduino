package com.example.ordermanagement.domain.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects every {@link DomainViolation} found by one or more validators, instead of failing on the first. */
public class ValidationNotification {

    private final List<DomainViolation> errors = new ArrayList<>();

    public void addError(String code, String propertyPath, String message) {
        errors.add(new DomainViolation(code, propertyPath, message));
    }

    public void addError(String propertyPath, String message) {
        errors.add(new DomainViolation(propertyPath, message));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<DomainViolation> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
