package com.auth.center.service;

import com.auth.center.entity.ConnectedApp;
import java.util.List;
import java.util.Map;

/**
 * Connected App 服务 -- 管理外部应用注册、密钥生成与验证.
 *
 * 安全原则：
 *
 *   - 密钥原文仅在创建时返回一次，此后只存 hash
 *   - 每个应用最多 2 个活跃密钥（支持无停机轮换）
 *   - 禁用应用后所有密钥立即失效
 */
public interface IConnectedAppService {

    /**
     * 查询所有 Connected App（管理员列表页）.
     *
     * @return 应用列表（含每个应用的活跃密钥摘要）
     */
    List<Map<String, Object>> listAll();

    /**
     * 创建 Connected App，并自动生成第一个密钥.
     *
     * @param name 应用名称
     * @param allowedDomains 域名白名单 JSON 数组字符串
     * @param targetSystem 目标系统（bi=洞察 / agent=知数 / tracking=循迹；空则默认 bi）
     * @param assertionPublicKeyPem Direct-Trust 断言验签公钥（PEM，可空）
     * @param createdBy 创建者用户 ID
     * @return {@code {app, clientId, secretId, secretValue}}（secretValue 仅此一次可见）
     */
    Map<String, Object> create(
            String name,
            String allowedDomains,
            String targetSystem,
            String assertionPublicKeyPem,
            Long createdBy);

    /**
     * 更新 Connected App 基本信息.
     *
     * @param id 应用 ID
     * @param name 应用名称
     * @param allowedDomains 域名白名单
     * @param targetSystem 目标系统（null 表示不改）
     * @param status 状态（enabled / disabled）
     * @param assertionPublicKeyPem 断言验签公钥（PEM，null 表示不改，空串清除）
     * @return 更新后的应用信息
     */
    ConnectedApp update(
            Long id,
            String name,
            String allowedDomains,
            String targetSystem,
            String status,
            String assertionPublicKeyPem);

    /**
     * 删除 Connected App（级联删除所有密钥）.
     *
     * @param id 应用 ID
     */
    void delete(Long id);

    /**
     * 为指定应用生成新密钥（最多 2 个）.
     *
     * @param appId 应用 ID
     * @return {@code {secretId, secretValue}}（secretValue 仅此一次可见）
     */
    Map<String, String> generateSecret(Long appId);

    /**
     * 撤销指定密钥.
     *
     * @param appId 应用 ID
     * @param secretId 密钥标识
     */
    void revokeSecret(Long appId, String secretId);

    /**
     * 验证 clientId + clientSecret 是否有效.
     *
     * @param clientId 应用标识
     * @param clientSecret 密钥原文
     * @return 有效的 ConnectedApp 实体；无效返回 {@code null}
     */
    ConnectedApp authenticate(String clientId, String clientSecret);

    /**
     * 验签 Direct-Trust 断言 -- 用应用注册的公钥（RS256）校验外部应用私钥签发的短时断言，提取其证明的终端用户标识（{@code sub}）.
     *
     * 校验项（任一不过即返回 null，fail-closed）：应用存在且启用、已配验签公钥、签名有效、 {@code aud} 含 {@code
     * bi-embed}、未过期且有效期不超过上限、{@code jti} 未被重放。
     *
     * @param clientId 应用标识
     * @param assertionJwt 外部应用私钥签发的断言 JWT
     * @return 校验通过的结果（含命中的 app 与终端用户 sub）；校验失败返回 {@code null}
     */
    AssertionResult verifyAssertion(String clientId, String assertionJwt);

    /**
     * Direct-Trust 断言校验结果.
     *
     * @param app 命中的 Connected App
     * @param subject 断言证明的终端用户标识（映射为本地用户名）
     */
    record AssertionResult(ConnectedApp app, String subject) {}
}
