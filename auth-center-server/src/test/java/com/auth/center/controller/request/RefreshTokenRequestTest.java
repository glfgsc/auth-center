package com.auth.center.controller.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@link RefreshTokenRequest} 的 Jakarta Validation 约束测试. */
class RefreshTokenRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPassWhenValid() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("eyJhbGciOiJSUzI1NiJ9.valid-token");
        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenNull() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(null);
        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenBlank() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("");
        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenWhitespace() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("   ");
        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenTooLong() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("x".repeat(2001));
        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }
}
