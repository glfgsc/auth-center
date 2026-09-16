package com.auth.center.service.impl;

import com.auth.center.entity.PlatformConfig;
import com.auth.center.mapper.PlatformConfigMapper;
import com.auth.center.service.IPlatformConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** {@link IPlatformConfigService} 默认实现。 */
@Service
public class PlatformConfigServiceImpl implements IPlatformConfigService {

    private final PlatformConfigMapper configMapper;

    /**
     * 构造注入。
     *
     * @param configMapper 平台配置 Mapper
     */
    public PlatformConfigServiceImpl(PlatformConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Override
    public List<PlatformConfig> list(String systemCode, String category) {
        LambdaQueryWrapper<PlatformConfig> wrapper = new LambdaQueryWrapper<>();
        if (systemCode != null && !systemCode.isBlank()) {
            wrapper.eq(PlatformConfig::getSystemCode, systemCode.trim());
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(PlatformConfig::getCategory, category.trim());
        }
        wrapper.orderByAsc(PlatformConfig::getSystemCode)
                .orderByAsc(PlatformConfig::getCategory)
                .orderByAsc(PlatformConfig::getSortOrder);
        return configMapper.selectList(wrapper);
    }

    @Override
    public PlatformConfig updateValue(Long id, String value, Long updatedBy) {
        PlatformConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("config not found: " + id);
        }
        validateType(config.getValueType(), value);
        config.setConfigValue(value);
        config.setUpdatedBy(updatedBy);
        configMapper.updateById(config);
        return config;
    }

    @Override
    public PlatformConfig resetToDefault(Long id, Long updatedBy) {
        PlatformConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("config not found: " + id);
        }
        config.setConfigValue(config.getDefaultValue());
        config.setUpdatedBy(updatedBy);
        configMapper.updateById(config);
        return config;
    }

    @Override
    public Map<String, String> getValues(String systemCode) {
        List<PlatformConfig> rows =
                configMapper.selectList(
                        new LambdaQueryWrapper<PlatformConfig>()
                                .eq(PlatformConfig::getSystemCode, systemCode));
        Map<String, String> values = new LinkedHashMap<>();
        for (PlatformConfig c : rows) {
            values.put(c.getConfigKey(), c.getConfigValue());
        }
        return values;
    }

    @Override
    public List<String> systems() {
        return configMapper
                .selectList(
                        new LambdaQueryWrapper<PlatformConfig>()
                                .select(PlatformConfig::getSystemCode)
                                .groupBy(PlatformConfig::getSystemCode)
                                .orderByAsc(PlatformConfig::getSystemCode))
                .stream()
                .map(PlatformConfig::getSystemCode)
                .toList();
    }

    /**
     * 按 valueType 轻校验新值合法性。
     *
     * @param valueType 值类型
     * @param value 新值(可空)
     * @throws IllegalArgumentException 值与类型不符
     */
    private void validateType(String valueType, String value) {
        if (value == null || value.isBlank() || valueType == null) {
            return;
        }
        String v = value.trim();
        try {
            switch (valueType) {
                case "INTEGER" -> Long.parseLong(v);
                case "DOUBLE" -> Double.parseDouble(v);
                case "BOOLEAN" -> {
                    if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                        throw new IllegalArgumentException("BOOLEAN 值须为 true / false");
                    }
                }
                default -> {
                    // STRING / JSON —— 不强校验(JSON 合法性由消费方兜底)。
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("值不符合类型 " + valueType + ": " + value);
        }
    }
}
