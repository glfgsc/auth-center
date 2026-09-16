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

/** {@link LoginRequest} 的 Jakarta Validation 约束测试. */
class LoginRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private LoginRequest validRequest() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("secret123");
        return req;
    }

    @Test
    void shouldPassWhenValid() {
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(validRequest());
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldPassWithService() {
        LoginRequest req = validRequest();
        req.setService("https://example.com/callback");
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenUsernameNull() {
        LoginRequest req = validRequest();
        req.setUsername(null);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenUsernameBlank() {
        LoginRequest req = validRequest();
        req.setUsername("");
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenUsernameWhitespace() {
        LoginRequest req = validRequest();
        req.setUsername("   ");
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenUsernameTooLong() {
        LoginRequest req = validRequest();
        req.setUsername("x".repeat(201));
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenPasswordNull() {
        LoginRequest req = validRequest();
        req.setPassword(null);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenPasswordBlank() {
        LoginRequest req = validRequest();
        req.setPassword("");
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        // 密码字段现为 RSA 密文，@NotBlank 违规（Size 已无下限）
        assertTrue(violations.size() >= 1);
    }

    @Test
    void shouldFailWhenPasswordTooLong() {
        LoginRequest req = validRequest();
        // 密码字段现为 RSA 密文，长度上限放宽到 1024；超限才违规。
        req.setPassword("x".repeat(1025));
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenServiceTooLong() {
        LoginRequest req = validRequest();
        req.setService("x".repeat(2001));
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }
}
