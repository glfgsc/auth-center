package com.auth.center.service;

import com.auth.center.entity.PlatformConfig;
import java.util.List;
import java.util.Map;

/**
 * 平台配置服务 —— 按系统分区的中心配置读写。
 *
 * 管理员经 admin 端点增改;各子系统经读端点取 {@link #getValues} 消费自己系统的中心配置。
 */
public interface IPlatformConfigService {

    /**
     * 列出配置(按系统 + 分类 + 排序)。
     *
     * @param systemCode 系统精确过滤(可空 = 全部)
     * @param category 分类精确过滤(可空)
     * @return 配置项列表
     */
    List<PlatformConfig> list(String systemCode, String category);

    /**
     * 更新一条配置的值(按 valueType 校验)。
     *
     * @param id 配置 id
     * @param value 新值
     * @param updatedBy 修改人 userId
     * @return 更新后的配置项
     */
    PlatformConfig updateValue(Long id, String value, Long updatedBy);

    /**
     * 重置一条配置为默认值。
     *
     * @param id 配置 id
     * @param updatedBy 修改人 userId
     * @return 重置后的配置项
     */
    PlatformConfig resetToDefault(Long id, Long updatedBy);

    /**
     * 取某系统的配置键值映射(供子系统消费)。
     *
     * @param systemCode 系统
     * @return 键 → 值
     */
    Map<String, String> getValues(String systemCode);

    /**
     * 列出所有有配置的系统 code(admin 页分组用)。
     *
     * @return 系统 code 列表
     */
    List<String> systems();
}
