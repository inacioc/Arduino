package com.example.ordermanagement.frontend.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Class-level constraint: no two order lines may reference the same product. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueProductsValidator.class)
public @interface UniqueProducts {

    String message() default "Each product can only appear once per order";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
