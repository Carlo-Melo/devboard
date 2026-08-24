package com.devboard.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reaproveitada por registro, alteração e redefinição de senha (spec-authentication.md, secao 6).
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Senha deve ter no mínimo 8 caracteres, com maiúscula, minúscula e número";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
