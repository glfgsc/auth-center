package com.auth.center.service;

import com.auth.center.entity.AuthSystem;
import com.auth.center.entity.SsoConfig;
import java.util.List;
import java.util.Map;

/**
 * SSO 配置服务接口 -- 管理外部 SSO 集成配置.
 *
 * 配置按产品存放，一个产品一行（{@code system_code} 对齐 {@code auth_system.code}），{@code global} 是兜底档。
 * 取配置分两种问法，不可混用:
 *
 *   - {@link #getConfig(String)} 精确取该产品自己那一行，没有就是 {@code null} —— 管理台编辑用。若这里也回落 {@code
 *       global}，管理员会把兜底档误认成本产品的配置，一保存就悄悄分叉出一份副本。
 *   - {@link #resolveConfig(String)} 带兜底解析:先按产品取，取不到回落 {@code global} —— 登录期用。
 */
public interface ISsoConfigService {

    /** 兜底档的产品编码 —— 产品没有自己那一行时登录期回落到它。 */
    String GLOBAL_SYSTEM_CODE = AuthSystem.CODE_GLOBAL;

    /**
     * 精确获取某产品自己的 SSO 配置（不回落兜底档）.
     *
     * @param systemCode 产品编码
     * @return 该产品的 SSO 配置；该产品未单独配置时返回 {@code null}
     */
    SsoConfig getConfig(String systemCode);

    /**
     * 解析某产品实际生效的 SSO 配置 —— 先按产品取，取不到回落 {@code global} 兜底档.
     *
     * @param systemCode 产品编码；为空时直接取兜底档
     * @return 实际生效的 SSO 配置，兜底档也不存在时返回 {@code null}
     */
    SsoConfig resolveConfig(String systemCode);

    /**
     * 列出所有已存在的 SSO 配置 —— 管理台标注哪些产品已单独配置.
     *
     * @return 全部配置行，按产品编码升序
     */
    List<SsoConfig> listConfigs();

    /**
     * 获取面向公开页面的 SSO 配置摘要（按 {@link #resolveConfig(String)} 的口径解析）.
     *
     * 仅包含前端渲染登录页所需的最小字段集: mode、displayName、serverUrl、enabled。
     *
     * @param systemCode 发起登录的产品编码；为空时取兜底档
     * @return 公开配置字段的 Map
     */
    Map<String, Object> getPublicConfig(String systemCode);

    /**
     * 保存某产品的 SSO 配置.
     *
     * 按 {@code (systemCode, type)} upsert:该产品该类型已有行则更新，否则新增。
     *
     * @param config 待保存的 SSO 配置实体，{@code systemCode} 必填
     * @return 保存后的 SSO 配置实体
     */
    SsoConfig updateConfig(SsoConfig config);

    /**
     * 删除某产品的 SSO 配置 —— 该产品回落到兜底档.
     *
     * @param systemCode 产品编码
     */
    void deleteConfig(String systemCode);
}
