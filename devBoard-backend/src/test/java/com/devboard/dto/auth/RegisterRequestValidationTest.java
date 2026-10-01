package com.devboard.dto.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Carlos-Dev", "octocat", "a", "dev-board-123"})
    void username_deveAceitarFormatoDoGithub(String username) {
        assertThat(validator.validate(requestWithUsername(username))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Carlos_Dev", "Carlos.Dev", "-Carlos", "Carlos-", "Carlos--Dev", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void username_deveRejeitarFormatoIncompativelComGithub(String username) {
        assertThat(validator.validate(requestWithUsername(username)))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("username"));
    }

    private RegisterRequest requestWithUsername(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail("carlos@example.com");
        request.setPassword("SecurePass123");
        request.setConfirmPassword("SecurePass123");
        return request;
    }
}
