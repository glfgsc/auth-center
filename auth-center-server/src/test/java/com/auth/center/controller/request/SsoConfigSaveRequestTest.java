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

/** {@link SsoConfigSaveRequest} 的 Jakarta Validation 约束测试. */
class SsoConfigSaveRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPassWhenAllNull() {
        // 所有字段可空（部分更新场景）
        SsoConfigSaveRequest req = new SsoConfigSaveRequest();
        Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldPassWhenAllFieldsPopulated() {
        SsoConfigSaveRequest req = new SsoConfigSaveRequest();
        req.setName("Enterprise SSO");
        req.setIcon("sso-icon");
        req.setLoginMode("enforced");
        req.setConfigJson("{\"serverUrl\":\"https://sso.example.com\"}");
        req.setEnabled(true);
        Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenNameTooLong() {
        SsoConfigSaveRequest req = new SsoConfigSaveRequest();
        req.setName("x".repeat(101));
        Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenIconTooLong() {
        SsoConfigSaveRequest req = new SsoConfigSaveRequest();
        req.setIcon("x".repeat(501));
        Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldPassWhenLoginModeIsAnyRunMode() {
        // 这三个即前端 IdpLoginMode 的全集，逐个放行
        for (String mode : new String[] {"disabled", "mixed", "enforced"}) {
            SsoConfigSaveRequest req = new SsoConfigSaveRequest();
            req.setLoginMode(mode);
            Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
            assertTrue(violations.isEmpty(), mode);
        }
    }

    @Test
    void shouldPassWhenLoginModeEmpty() {
        // 空串与 null 一样放行：部分更新时该字段可不给
        SsoConfigSaveRequest req = new SsoConfigSaveRequest();
        req.setLoginMode("");
        Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenLoginModeNotARunMode() {
        // cas 是协议名不是运行模式，落库后登录页会按未知模式渲染
        SsoConfigSaveRequest req = new SsoConfigSaveRequest();
        req.setLoginMode("cas");
        Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }

    @Test
    void shouldFailWhenLoginModeTooLong() {
        // 超出 auth_sso_config.mode 的 VARCHAR(20)，且必然不在枚举内，@Size 与 @Pattern 各报一条
        SsoConfigSaveRequest req = new SsoConfigSaveRequest();
        req.setLoginMode("x".repeat(21));
        Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
        assertEquals(2, violations.size());
    }

    @Test
    void shouldFailWhenConfigJsonTooLong() {
        SsoConfigSaveRequest req = new SsoConfigSaveRequest();
        req.setConfigJson("x".repeat(4001));
        Set<ConstraintViolation<SsoConfigSaveRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
    }
}
