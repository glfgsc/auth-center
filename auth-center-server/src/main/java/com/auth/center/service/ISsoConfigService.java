package com.auth.center.service;

import com.auth.center.entity.SsoConfig;

import java.util.Map;

/**
 * SSO 配置服务接口 -- 管理外部 SSO 集成配置.
 *
 * <p>提供 SSO 配置的标准 CRUD 操作，以及面向前端的公开配置查询。</p>
 */
public interface ISsoConfigService {

    /**
     * 获取当前 SSO 配置（完整信息，仅管理员可见）.
     *
     * @return SSO 配置实体，不存在时返回 {@code null}
     */
    SsoConfig getConfig();

    /**
     * 获取面向公开页面的 SSO 配置摘要.
     *
     * <p>仅包含前端渲染登录页所需的最小字段集:
     * mode、displayName、serverUrl、enabled。</p>
     *
     * @return 公开配置字段的 Map
     */
    Map<String, Object> getPublicConfig();

    /**
     * 更新 SSO 配置.
     *
     * <p>若数据库中不存在配置记录，则新增；否则更新已有记录。</p>
     *
     * @param config 待保存的 SSO 配置实体
     * @return 保存后的 SSO 配置实体
     */
    SsoConfig updateConfig(SsoConfig config);
}
