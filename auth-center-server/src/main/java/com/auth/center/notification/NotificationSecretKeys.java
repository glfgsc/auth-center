package com.auth.center.notification;

/**
 * 通知渠道秘密 JSON 的键名.
 *
 * 与洞察侧 {@code CredentialSecretKeys} 逐字相同 -- bi_named_credential 的存量数据是按这些键写的,
 * 键名一变迁移过来就取不出值,且症状是"渠道在、测试却连不上", 很难往键名上想.
 *
 * 单独成类而不是塞进 {@link NotificationChannelType}: 枚举常量必须是类体首元素,在它们的构造参数里引用同类后面声明的静态字段是非法前向引用.
 */
public final class NotificationSecretKeys {

    /** webhook 地址 -- 它本身就是凭据(企微 ?key= / 飞书 /hook/&lt;token&gt; / 钉钉 ?access_token=). */
    public static final String WEBHOOK_URL = "webhook_url";

    /** 钉钉 / 飞书的加签密钥. */
    public static final String SIGN_SECRET = "sign_secret";

    /** 企业微信企业 ID. */
    public static final String CORP_ID = "corp_id";

    /** 企业微信应用 AgentId. */
    public static final String AGENT_ID = "agent_id";

    /** 企业微信应用 Secret. */
    public static final String CORP_SECRET = "corp_secret";

    /** SMTP 服务器地址. */
    public static final String SMTP_HOST = "host";

    /** SMTP 端口. */
    public static final String SMTP_PORT = "port";

    /** SMTP 账号. */
    public static final String SMTP_USERNAME = "username";

    /** SMTP 口令. */
    public static final String SMTP_PASSWORD = "password";

    /** SMTP 发件人地址. */
    public static final String SMTP_FROM = "from";

    /** SMTP 是否启用 TLS. */
    public static final String SMTP_USE_TLS = "useTls";

    /** HTTP Bearer 令牌. */
    public static final String HTTP_BEARER_TOKEN = "bearerToken";

    /** HTTP Basic 用户名. */
    public static final String HTTP_BASIC_USERNAME = "basicUsername";

    /** HTTP Basic 口令. */
    public static final String HTTP_BASIC_PASSWORD = "basicPassword";

    private NotificationSecretKeys() {}
}
