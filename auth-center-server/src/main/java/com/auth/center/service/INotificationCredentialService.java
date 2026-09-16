package com.auth.center.service;

import com.auth.center.entity.NotificationCredential;
import java.util.List;
import java.util.Map;

/**
 * 通知渠道凭据服务 -- 跨产品统一的「用哪个凭据」这一层.
 *
 * 与 V25 的渠道开关分工:开关回答「这个产品允不允许用钉钉」,本服务回答「用哪一个钉钉群」. 前者是明文 KV, 后者的地址与加签密钥是秘密,需要可逆加密与读回掩码.
 */
public interface INotificationCredentialService {

    /** 读回时替换秘密内容的掩码 -- 秘密只进不出. */
    String SECRET_MASK = "***";

    /**
     * 按 key 解析出秘密内容 -- 仅内部服务通道可调,返回的是解密后的明文.
     *
     * @param targetSystem 归属产品
     * @param credKey 引用名
     * @param expectedType 期望的渠道类型;传 null 表示不校验类型
     * @return 秘密键值对;未找到 / 已停用 / 类型不匹配时返回 null
     */
    Map<String, Object> resolve(String targetSystem, String credKey, String expectedType);

    /**
     * 管理面列表 -- 秘密内容已置为 {@link #SECRET_MASK}.
     *
     * @param targetSystem 归属产品;必填
     * @param scopeRefs 作用域引用集合,命中其一即返回;传 null 表示不按作用域过滤(管理员看全部)
     * @param credType 渠道类型;可空
     * @return 凭据列表,无作用域的排在前面
     */
    List<NotificationCredential> list(String targetSystem, List<String> scopeRefs, String credType);

    /**
     * 按 ID 查询 -- 秘密内容已置为 {@link #SECRET_MASK}.
     *
     * @param id 凭据 ID
     * @return 凭据;不存在时返回 null
     */
    NotificationCredential getById(Long id);

    /**
     * 新建或更新. 秘密留空或等于掩码时保持原值不变,变更时刷新 {@code lastRotatedAt}.
     *
     * @param row 凭据实体
     * @param actingUserId 操作者用户 ID
     * @return 凭据 ID
     */
    Long save(NotificationCredential row, Long actingUserId);

    /**
     * 软删除.
     *
     * @param id 凭据 ID
     */
    void delete(Long id);

    /**
     * 连通性测试 -- 向上游真发一条探测消息,结果落库到 {@code lastTest*} 三列.
     *
     * @param id 凭据 ID
     * @return 测试结果
     */
    TestOutcome test(Long id);

    /**
     * 测试结果.
     *
     * @param ok 是否成功
     * @param message 结果说明;绝不含完整 URL -- 地址本身是凭据
     * @param durationMs 耗时毫秒
     */
    record TestOutcome(boolean ok, String message, long durationMs) {}
}
