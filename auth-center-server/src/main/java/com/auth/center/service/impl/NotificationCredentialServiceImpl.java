package com.auth.center.service.impl;

import com.auth.center.entity.NotificationCredential;
import com.auth.center.mapper.NotificationCredentialMapper;
import com.auth.center.notification.NotificationProbe;
import com.auth.center.service.INotificationCredentialService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知渠道凭据服务实现. */
@Service
public class NotificationCredentialServiceImpl implements INotificationCredentialService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationCredentialServiceImpl.class);

    private final NotificationCredentialMapper mapper;
    private final NotificationProbe probe;
    private final ObjectMapper objectMapper;

    /**
     * 构造注入.
     *
     * @param mapper 凭据 Mapper
     * @param probe 连通性探测器
     * @param objectMapper JSON 解析器
     */
    public NotificationCredentialServiceImpl(
            NotificationCredentialMapper mapper,
            NotificationProbe probe,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.probe = probe;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> resolve(String targetSystem, String credKey, String expectedType) {
        if (targetSystem == null || credKey == null || credKey.isBlank()) {
            return null;
        }
        NotificationCredential row = findByKey(targetSystem, credKey);
        if (row == null || !isEnabled(row)) {
            return null;
        }
        if (expectedType != null && !expectedType.equals(row.getCredType())) {
            log.warn(
                    "[NotifCred] type mismatch for key={} system={}: stored={} expected={}",
                    credKey,
                    targetSystem,
                    row.getCredType(),
                    expectedType);
            return null;
        }
        return parseSecret(row);
    }

    @Override
    public List<NotificationCredential> list(
            String targetSystem, List<String> scopeRefs, String credType) {
        QueryWrapper<NotificationCredential> w = new QueryWrapper<>();
        // 省略归属即不收窄 —— 管理台顶栏的「全部产品」档要一眼看到所有渠道。
        // 内部服务通道那两个端点的 targetSystem 是必填的,不会走到这里。
        if (targetSystem != null && !targetSystem.isBlank()) {
            w.eq("target_system", targetSystem);
        }
        if (credType != null && !credType.isBlank()) {
            w.eq("cred_type", credType);
        }
        if (scopeRefs != null) {
            // 无作用域(NULL)= 该产品内全局可用,任何调用方都该看到;其余按传入的引用集合命中。
            if (scopeRefs.isEmpty()) {
                w.isNull("scope_ref");
            } else {
                w.and(q -> q.isNull("scope_ref").or().in("scope_ref", scopeRefs));
            }
        }
        // 全局的排在前面 —— 它们是默认可用的那批。MySQL 里布尔表达式即 0/1,false 先出。
        w.last("ORDER BY (scope_ref IS NOT NULL), updated_at DESC");
        List<NotificationCredential> rows = mapper.selectList(w);
        rows.forEach(NotificationCredentialServiceImpl::mask);
        return rows;
    }

    @Override
    public NotificationCredential getById(Long id) {
        NotificationCredential row = mapper.selectById(id);
        if (row != null) {
            mask(row);
        }
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(NotificationCredential row, Long actingUserId) {
        boolean secretProvided = hasRealSecret(row.getEncryptedSecretJson());
        if (!secretProvided) {
            // 掩码或空 —— 保持原值。MP 的 updateById 是 NOT_NULL 策略,null 字段不进 SQL。
            row.setEncryptedSecretJson(null);
        }

        if (row.getId() == null) {
            if (!secretProvided) {
                throw new IllegalArgumentException("新建渠道必须提供凭据内容");
            }
            row.setCreatedBy(actingUserId);
            row.setLastRotatedAt(LocalDateTime.now());
            mapper.insert(row);
            return row.getId();
        }

        // 归属与引用名建后不可改:它们是别处引用这条凭据的锚点,改了等于把引用悬空。
        NotificationCredential existing = mapper.selectById(row.getId());
        if (existing == null) {
            throw new IllegalArgumentException("渠道不存在或已删除");
        }
        row.setTargetSystem(null);
        row.setCredKey(null);
        row.setCredType(null);
        row.setScopeRef(null);
        if (secretProvided) {
            row.setLastRotatedAt(LocalDateTime.now());
        }
        mapper.updateById(row);
        return row.getId();
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public TestOutcome test(Long id) {
        NotificationCredential row = mapper.selectById(id);
        if (row == null) {
            return new TestOutcome(false, "渠道不存在或已删除", 0L);
        }
        Map<String, Object> secret = parseSecret(row);
        if (secret == null) {
            return new TestOutcome(false, "凭据内容无法解析,可能是密钥已轮换", 0L);
        }

        long started = System.nanoTime();
        NotificationProbe.Result result = probe.probe(row.getCredType(), secret);
        long durationMs = (System.nanoTime() - started) / 1_000_000L;

        NotificationCredential patch = new NotificationCredential();
        patch.setId(id);
        patch.setLastTestStatus(result.ok() ? "OK" : "FAILED");
        patch.setLastTestAt(LocalDateTime.now());
        patch.setLastTestMessage(truncate(result.message()));
        mapper.updateById(patch);

        return new TestOutcome(result.ok(), result.message(), durationMs);
    }

    private NotificationCredential findByKey(String targetSystem, String credKey) {
        QueryWrapper<NotificationCredential> w = new QueryWrapper<>();
        w.eq("target_system", targetSystem).eq("cred_key", credKey).last("LIMIT 1");
        return mapper.selectOne(w);
    }

    private Map<String, Object> parseSecret(NotificationCredential row) {
        String json = row.getEncryptedSecretJson();
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // 不打印 json 本身 —— 它就是秘密。
            log.warn(
                    "[NotifCred] secret json unparseable for id={} key={}",
                    row.getId(),
                    row.getCredKey());
            return null;
        }
    }

    private static boolean isEnabled(NotificationCredential row) {
        return row.getEnabled() == null || row.getEnabled() != 0;
    }

    /** 秘密只进不出 —— 读回一律掩码,包括管理员. */
    private static void mask(NotificationCredential row) {
        row.setEncryptedSecretJson(SECRET_MASK);
    }

    private static boolean hasRealSecret(String value) {
        return value != null && !value.isBlank() && !SECRET_MASK.equals(value);
    }

    private static String truncate(String message) {
        if (message == null) return null;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
