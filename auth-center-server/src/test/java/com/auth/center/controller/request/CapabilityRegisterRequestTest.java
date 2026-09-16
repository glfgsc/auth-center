package com.auth.center.controller.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@link CapabilityRegisterRequest} 的 Jakarta Validation 约束测试. */
class CapabilityRegisterRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private CapabilityRegisterRequest.CapabilityItem validItem() {
        CapabilityRegisterRequest.CapabilityItem item =
                new CapabilityRegisterRequest.CapabilityItem();
        item.setCode("dashboard:view");
        item.setCategory("dashboard");
        item.setLabel("View Dashboard");
        item.setDescription("Allows viewing dashboards");
        return item;
    }

    private CapabilityRegisterRequest validRequest() {
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(List.of(validItem()));
        return req;
    }

    @Test
    void shouldPassWhenValid() {
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations =
                validator.validate(validRequest());
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenSystemCodeNull() {
        CapabilityRegisterRequest req = validRequest();
        req.setSystemCode(null);
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenSystemCodeBlank() {
        CapabilityRegisterRequest req = validRequest();
        req.setSystemCode("");
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenSystemCodeWhitespace() {
        CapabilityRegisterRequest req = validRequest();
        req.setSystemCode("   ");
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenSystemCodeTooLong() {
        CapabilityRegisterRequest req = validRequest();
        req.setSystemCode("x".repeat(51));
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenCapabilitiesNull() {
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(null);
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenCapabilitiesEmpty() {
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(Collections.emptyList());
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenItemCodeNull() {
        CapabilityRegisterRequest.CapabilityItem item = validItem();
        item.setCode(null);
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(List.of(item));
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailWhenItemCodeBlank() {
        CapabilityRegisterRequest.CapabilityItem item = validItem();
        item.setCode("");
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(List.of(item));
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailWhenItemCodeTooLong() {
        CapabilityRegisterRequest.CapabilityItem item = validItem();
        item.setCode("x".repeat(101));
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(List.of(item));
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailWhenItemLabelTooLong() {
        CapabilityRegisterRequest.CapabilityItem item = validItem();
        item.setLabel("x".repeat(201));
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(List.of(item));
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailWhenItemCategoryTooLong() {
        CapabilityRegisterRequest.CapabilityItem item = validItem();
        item.setCategory("x".repeat(51));
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(List.of(item));
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailWhenItemDescriptionTooLong() {
        CapabilityRegisterRequest.CapabilityItem item = validItem();
        item.setDescription("x".repeat(501));
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(List.of(item));
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldPassWhenItemNameAndDescriptionNull() {
        CapabilityRegisterRequest.CapabilityItem item =
                new CapabilityRegisterRequest.CapabilityItem();
        item.setCode("test:cap");
        // name and description are nullable
        CapabilityRegisterRequest req = new CapabilityRegisterRequest();
        req.setSystemCode("bi");
        req.setCapabilities(List.of(item));
        Set<ConstraintViolation<CapabilityRegisterRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }
}
