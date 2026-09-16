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

/** {@link AssignPermissionSetRequest} 的 Jakarta Validation 约束测试. */
class AssignPermissionSetRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPassWhenValid() {
        AssignPermissionSetRequest req = new AssignPermissionSetRequest();
        req.setPermissionSetCode("admin");
        Set<ConstraintViolation<AssignPermissionSetRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenNull() {
        AssignPermissionSetRequest req = new AssignPermissionSetRequest();
        req.setPermissionSetCode(null);
        Set<ConstraintViolation<AssignPermissionSetRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenBlank() {
        AssignPermissionSetRequest req = new AssignPermissionSetRequest();
        req.setPermissionSetCode("");
        Set<ConstraintViolation<AssignPermissionSetRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenWhitespace() {
        AssignPermissionSetRequest req = new AssignPermissionSetRequest();
        req.setPermissionSetCode("   ");
        Set<ConstraintViolation<AssignPermissionSetRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenTooLong() {
        AssignPermissionSetRequest req = new AssignPermissionSetRequest();
        req.setPermissionSetCode("x".repeat(51));
        Set<ConstraintViolation<AssignPermissionSetRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldPassAtMaxBoundary() {
        AssignPermissionSetRequest req = new AssignPermissionSetRequest();
        req.setPermissionSetCode("x".repeat(50));
        Set<ConstraintViolation<AssignPermissionSetRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }
}
