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

/** {@link TicketValidateRequest} 的 Jakarta Validation 约束测试. */
class TicketValidateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private TicketValidateRequest validRequest() {
        TicketValidateRequest req = new TicketValidateRequest();
        req.setTicket("ST-12345-abc");
        req.setService("https://app.example.com/sso-callback");
        return req;
    }

    @Test
    void shouldPassWhenValid() {
        Set<ConstraintViolation<TicketValidateRequest>> violations =
                validator.validate(validRequest());
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenTicketNull() {
        TicketValidateRequest req = validRequest();
        req.setTicket(null);
        Set<ConstraintViolation<TicketValidateRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenTicketBlank() {
        TicketValidateRequest req = validRequest();
        req.setTicket("");
        Set<ConstraintViolation<TicketValidateRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenTicketWhitespace() {
        TicketValidateRequest req = validRequest();
        req.setTicket("   ");
        Set<ConstraintViolation<TicketValidateRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenTicketTooLong() {
        TicketValidateRequest req = validRequest();
        req.setTicket("x".repeat(501));
        Set<ConstraintViolation<TicketValidateRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenServiceNull() {
        TicketValidateRequest req = validRequest();
        req.setService(null);
        Set<ConstraintViolation<TicketValidateRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenServiceBlank() {
        TicketValidateRequest req = validRequest();
        req.setService("");
        Set<ConstraintViolation<TicketValidateRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenServiceTooLong() {
        TicketValidateRequest req = validRequest();
        req.setService("x".repeat(501));
        Set<ConstraintViolation<TicketValidateRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }
}
