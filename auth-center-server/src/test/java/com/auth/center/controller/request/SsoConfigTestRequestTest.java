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

/** {@link SsoConfigTestRequest} 的 Jakarta Validation 约束测试. */
class SsoConfigTestRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPassWhenNull() {
        SsoConfigTestRequest req = new SsoConfigTestRequest();
        Set<ConstraintViolation<SsoConfigTestRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldPassWhenValid() {
        SsoConfigTestRequest req = new SsoConfigTestRequest();
        req.setConfigJson("{\"serverUrl\":\"https://sso.example.com\"}");
        Set<ConstraintViolation<SsoConfigTestRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenTooLong() {
        SsoConfigTestRequest req = new SsoConfigTestRequest();
        req.setConfigJson("x".repeat(4001));
        Set<ConstraintViolation<SsoConfigTestRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldPassAtMaxBoundary() {
        SsoConfigTestRequest req = new SsoConfigTestRequest();
        req.setConfigJson("x".repeat(4000));
        Set<ConstraintViolation<SsoConfigTestRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }
}
