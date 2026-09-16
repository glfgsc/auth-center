package com.auth.center.service.impl;

import com.auth.center.entity.SsoConfig;
import com.auth.center.mapper.SsoConfigMapper;
import com.auth.center.service.ISsoConfigService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SSO 配置服务实现 -- 管理外部 SSO 集成配置.
 *
 * 一个产品一行，唯一键 {@code (system_code, type)}；{@code global} 为兜底档。
 */
@Service
public class SsoConfigServiceImpl implements ISsoConfigService {

    private static final Logger log = LoggerFactory.getLogger(SsoConfigServiceImpl.class);

    /** 公开配置字段:模式 */
    private static final String FIELD_MODE = "mode";

    /** 公开配置字段:显示名称 */
    private static final String FIELD_DISPLAY_NAME = "displayName";

    /** 公开配置字段:服务器地址 */
    private static final String FIELD_SERVER_URL = "serverUrl";

    /** 公开配置字段:是否启用 */
    private static final String FIELD_ENABLED = "enabled";

    /** 默认 SSO 模式:关闭 */
    private static final String DEFAULT_MODE = "disabled";

    /** 表列名:所属产品 */
    private static final String COL_SYSTEM_CODE = "system_code";

    /** 表列名: SSO 类型 */
    private static final String COL_TYPE = "type";

    private final SsoConfigMapper ssoConfigMapper;

    /**
     * 构造函数，注入 SSO 配置 Mapper.
     *
     * @param ssoConfigMapper SSO 配置 Mapper
     */
    public SsoConfigServiceImpl(SsoConfigMapper ssoConfigMapper) {
        this.ssoConfigMapper = ssoConfigMapper;
    }

    /** {@inheritDoc} */
    @Override
    public SsoConfig getConfig(String systemCode) {
        if (systemCode == null || systemCode.isBlank()) {
            return null;
        }
        QueryWrapper<SsoConfig> wrapper = new QueryWrapper<>();
        wrapper.eq(COL_SYSTEM_CODE, systemCode).orderByAsc(COL_TYPE).last("LIMIT 1");
        return ssoConfigMapper.selectOne(wrapper);
    }

    /** {@inheritDoc} */
    @Override
    public SsoConfig resolveConfig(String systemCode) {
        SsoConfig own = getConfig(systemCode);
        if (own != null) {
            return own;
        }
        return getConfig(GLOBAL_SYSTEM_CODE);
    }

    /** {@inheritDoc} */
    @Override
    public List<SsoConfig> listConfigs() {
        QueryWrapper<SsoConfig> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc(COL_SYSTEM_CODE);
        return ssoConfigMapper.selectList(wrapper);
    }

    /**
     * {@inheritDoc}
     *
     * 返回前端 CasPublicConfig 所需的字段: enabled, mode, name, icon, loginUrl。
     */
    @Override
    public Map<String, Object> getPublicConfig(String systemCode) {
        Map<String, Object> result = new HashMap<>();
        SsoConfig config = resolveConfig(systemCode);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            result.put(FIELD_MODE, DEFAULT_MODE);
            result.put(FIELD_DISPLAY_NAME, "");
            result.put(FIELD_SERVER_URL, "");
            result.put(FIELD_ENABLED, false);
            result.put("name", "");
            result.put("icon", "");
            result.put("loginUrl", null);
            return result;
        }
        result.put(FIELD_MODE, config.getMode());
        result.put(FIELD_DISPLAY_NAME, config.getDisplayName());
        result.put(FIELD_SERVER_URL, config.getServerUrl());
        result.put(FIELD_ENABLED, config.getEnabled());
        // 前端 CasPublicConfig 需要的额外字段
        result.put("name", config.getDisplayName() != null ? config.getDisplayName() : "CAS");
        result.put("icon", config.getIcon() != null ? config.getIcon() : "");
        // loginUrl: 拼接 CAS 登录端点供前端 mixed 模式按钮使用
        String serverUrl = config.getServerUrl();
        if (serverUrl != null && !serverUrl.isBlank()) {
            result.put("loginUrl", serverUrl.replaceAll("/+$", "") + "/login");
        } else {
            result.put("loginUrl", null);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public SsoConfig updateConfig(SsoConfig config) {
        // 产品编码不给默认值:静默落到 global 会把所有未单独配置的产品一起改掉。
        if (config.getSystemCode() == null || config.getSystemCode().isBlank()) {
            throw new IllegalArgumentException("SSO 配置必须指明所属产品(systemCode)");
        }
        SsoConfig existing = findBySystemAndType(config.getSystemCode(), config.getType());
        if (existing == null) {
            ssoConfigMapper.insert(config);
            log.info(
                    "SSO 配置已新增: systemCode={}, type={}, mode={}",
                    config.getSystemCode(),
                    config.getType(),
                    config.getMode());
        } else {
            config.setId(existing.getId());
            ssoConfigMapper.updateById(config);
            log.info(
                    "SSO 配置已更新: systemCode={}, type={}, mode={}",
                    config.getSystemCode(),
                    config.getType(),
                    config.getMode());
        }
        return config;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteConfig(String systemCode) {
        SsoConfig existing = getConfig(systemCode);
        if (existing != null) {
            ssoConfigMapper.deleteById(existing.getId());
            log.info("SSO 配置已删除: systemCode={}, id={}", systemCode, existing.getId());
        }
    }

    /**
     * 按 {@code (system_code, type)} 唯一键取行 —— upsert 的判据。
     *
     * @param systemCode 产品编码
     * @param type SSO 类型，为空时退化为「该产品的第一行」
     * @return 命中的配置行，无则 {@code null}
     */
    private SsoConfig findBySystemAndType(String systemCode, String type) {
        if (type == null || type.isBlank()) {
            return getConfig(systemCode);
        }
        QueryWrapper<SsoConfig> wrapper = new QueryWrapper<>();
        wrapper.eq(COL_SYSTEM_CODE, systemCode).eq(COL_TYPE, type);
        return ssoConfigMapper.selectOne(wrapper);
    }
}
