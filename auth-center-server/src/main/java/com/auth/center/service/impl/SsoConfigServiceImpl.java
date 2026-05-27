package com.auth.center.service.impl;

import com.auth.center.entity.SsoConfig;
import com.auth.center.mapper.SsoConfigMapper;
import com.auth.center.service.ISsoConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SSO 配置服务实现 -- 管理外部 SSO 集成配置的增删改查.
 *
 * <p>当前设计为单行记录模式（只保存一条 SSO 配置），
 * 后续可扩展为多 SSO 源配置。</p>
 */
@Service
public class SsoConfigServiceImpl implements ISsoConfigService {

    private static final Logger log = LoggerFactory.getLogger(SsoConfigServiceImpl.class);

    /** 公开配置字段: 模式 */
    private static final String FIELD_MODE = "mode";

    /** 公开配置字段: 显示名称 */
    private static final String FIELD_DISPLAY_NAME = "displayName";

    /** 公开配置字段: 服务器地址 */
    private static final String FIELD_SERVER_URL = "serverUrl";

    /** 公开配置字段: 是否启用 */
    private static final String FIELD_ENABLED = "enabled";

    /** 默认 SSO 模式: 关闭 */
    private static final String DEFAULT_MODE = "disabled";

    private final SsoConfigMapper ssoConfigMapper;

    /**
     * 构造函数，注入 SSO 配置 Mapper.
     *
     * @param ssoConfigMapper SSO 配置 Mapper
     */
    public SsoConfigServiceImpl(SsoConfigMapper ssoConfigMapper) {
        this.ssoConfigMapper = ssoConfigMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SsoConfig getConfig() {
        List<SsoConfig> configs = ssoConfigMapper.selectList(null);
        if (configs.isEmpty()) {
            return null;
        }
        return configs.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getPublicConfig() {
        Map<String, Object> result = new HashMap<>();
        SsoConfig config = getConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            result.put(FIELD_MODE, DEFAULT_MODE);
            result.put(FIELD_DISPLAY_NAME, "");
            result.put(FIELD_SERVER_URL, "");
            result.put(FIELD_ENABLED, false);
            return result;
        }
        result.put(FIELD_MODE, config.getMode());
        result.put(FIELD_DISPLAY_NAME, config.getDisplayName());
        result.put(FIELD_SERVER_URL, config.getServerUrl());
        result.put(FIELD_ENABLED, config.getEnabled());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SsoConfig updateConfig(SsoConfig config) {
        SsoConfig existing = getConfig();
        if (existing == null) {
            ssoConfigMapper.insert(config);
            log.info("SSO 配置已新增: type={}, mode={}", config.getType(), config.getMode());
        } else {
            config.setId(existing.getId());
            ssoConfigMapper.updateById(config);
            log.info("SSO 配置已更新: type={}, mode={}", config.getType(), config.getMode());
        }
        return config;
    }
}
