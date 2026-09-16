package com.auth.center.controller.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@link GroupMemberRequest} 的 Jakarta Validation 约束测试. */
class GroupMemberRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPassWhenValid() {
        GroupMemberRequest req = new GroupMemberRequest();
        req.setUserIds(List.of(1L, 2L, 3L));
        Set<ConstraintViolation<GroupMemberRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenUserIdsNull() {
        GroupMemberRequest req = new GroupMemberRequest();
        req.setUserIds(null);
        Set<ConstraintViolation<GroupMemberRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenUserIdsEmpty() {
        GroupMemberRequest req = new GroupMemberRequest();
        req.setUserIds(Collections.emptyList());
        Set<ConstraintViolation<GroupMemberRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenUserIdContainsNull() {
        GroupMemberRequest req = new GroupMemberRequest();
        List<Long> ids = new ArrayList<>();
        ids.add(1L);
        ids.add(null);
        req.setUserIds(ids);
        Set<ConstraintViolation<GroupMemberRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailWhenUserIdNegative() {
        GroupMemberRequest req = new GroupMemberRequest();
        req.setUserIds(List.of(1L, -5L));
        Set<ConstraintViolation<GroupMemberRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailWhenUserIdZero() {
        GroupMemberRequest req = new GroupMemberRequest();
        req.setUserIds(List.of(0L));
        Set<ConstraintViolation<GroupMemberRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldPassWhenSingleUserId() {
        GroupMemberRequest req = new GroupMemberRequest();
        req.setUserIds(List.of(42L));
        Set<ConstraintViolation<GroupMemberRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }
}
