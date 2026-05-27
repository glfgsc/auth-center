package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.SystemCapability;
import com.auth.center.mapper.SystemCapabilityMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 跨系统能力注册控制器 -- 各子系统向认证中心注册和查询能力码.
 *
 * <p>能力码是权限模型的最小粒度单元，子系统启动时通过 {@code /api/capabilities/register}
 * 端点批量注册自身提供的能力，认证中心统一管理和分发。</p>
 */
@RestController
@RequestMapping("/api/auth/capabilities")
public class CapabilityController {

    private static final Logger log = LoggerFactory.getLogger(CapabilityController.class);

    /** 请求体字段: 子系统编码 */
    private static final String FIELD_SYSTEM_CODE = "systemCode";

    /** 请求体字段: 能力列表 */
    private static final String FIELD_CAPABILITIES = "capabilities";

    private final SystemCapabilityMapper systemCapabilityMapper;

    /**
     * 构造函数，注入系统能力 Mapper.
     *
     * @param systemCapabilityMapper 系统能力 Mapper
     */
    public CapabilityController(SystemCapabilityMapper systemCapabilityMapper) {
        this.systemCapabilityMapper = systemCapabilityMapper;
    }

    /**
     * 批量注册子系统能力码.
     *
     * <p>先删除该系统编码下所有旧能力码，再批量插入新的。
     * 相当于每次注册都是全量替换。</p>
     *
     * <p>请求体格式:
     * <pre>
     * {
     *   "systemCode": "bi",
     *   "capabilities": [
     *     {"code": "dashboard:view", "category": "dashboard", "label": "查看仪表盘", "description": "..."},
     *     {"code": "dataset:create", "category": "dataset", "label": "创建数据集", "description": "..."}
     *   ]
     * }
     * </pre>
     *
     * @param body 请求体
     * @return 注册结果，包含注册的能力数量
     */
    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        String systemCode = (String) body.get(FIELD_SYSTEM_CODE);
        if (systemCode == null || systemCode.isBlank()) {
            return Result.fail("systemCode 不能为空");
        }

        Object capObj = body.get(FIELD_CAPABILITIES);
        if (!(capObj instanceof List<?>)) {
            return Result.fail("capabilities 必须为数组");
        }
        List<Map<String, String>> capabilities = (List<Map<String, String>>) capObj;

        // 删除该系统编码下所有旧能力码
        LambdaQueryWrapper<SystemCapability> deleteQuery = new LambdaQueryWrapper<>();
        deleteQuery.eq(SystemCapability::getSystemCode, systemCode);
        systemCapabilityMapper.delete(deleteQuery);

        // 批量插入新能力码
        int count = 0;
        for (Map<String, String> cap : capabilities) {
            SystemCapability entity = new SystemCapability();
            entity.setSystemCode(systemCode);
            entity.setCapabilityCode(cap.get("code"));
            entity.setCategory(cap.get("category"));
            entity.setLabel(cap.get("label"));
            entity.setDescription(cap.get("description"));
            systemCapabilityMapper.insert(entity);
            count++;
        }

        log.info("系统 {} 注册了 {} 个能力码", systemCode, count);
        return Result.ok(Map.of("systemCode", systemCode, "registered", count));
    }

    /**
     * 查询已注册的能力码列表.
     *
     * <p>可通过 systemCode 参数筛选特定子系统的能力码。
     * 不传参数则返回所有已注册能力码。</p>
     *
     * @param systemCode 子系统编码（可选），如 "bi"、"flow"
     * @return 能力码列表
     */
    @GetMapping
    public Result<List<SystemCapability>> list(
            @RequestParam(required = false) String systemCode) {
        LambdaQueryWrapper<SystemCapability> query = new LambdaQueryWrapper<>();
        if (systemCode != null && !systemCode.isBlank()) {
            query.eq(SystemCapability::getSystemCode, systemCode);
        }
        query.orderByAsc(SystemCapability::getSystemCode, SystemCapability::getCategory);
        return Result.ok(systemCapabilityMapper.selectList(query));
    }
}
