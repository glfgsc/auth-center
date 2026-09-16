package com.auth.center.security;

/**
 * 会话吊销原因码 —— 后端只给码,话术在前端.
 *
 * 码随 {@link SessionKeys#revokedKey} 的值落进 Redis,由网关在 401 响应里原样回带,前端据此决定跳登录后显示哪一句。新增码时三处要同改:本类、前端的码→文案表、网关
 * 401 的透传(网关只搬运不认码,故通常无需改动)。
 */
public final class SessionRevokeReasons {

    /** 同一账号在别处登录,本会话按并发上限被顶下线。 */
    public static final String CONCURRENT_LOGIN = "CONCURRENT_LOGIN";

    /** 管理员在会话治理控制台手工吊销。 */
    public static final String ADMIN_REVOKED = "ADMIN_REVOKED";

    /** 用户主动登出。 */
    public static final String LOGOUT = "LOGOUT";

    /** 常量类不实例化。 */
    private SessionRevokeReasons() {}
}
