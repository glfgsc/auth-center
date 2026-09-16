package com.auth.center.notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 通知渠道类型 -- 每种类型声明它需要哪些秘密字段,前端据此渲染表单.
 *
 * 新增一家厂商只需在这里加一个枚举项:前端表单、探测分派、渠道开关归属都会自动跟上,不必改前端代码.
 *
 * @see NotificationSecretKeys 键名与洞察侧逐字相同的原因
 */
public enum NotificationChannelType {

    /** 企业微信群机器人. */
    WECOM_BOT("企业微信群机器人", "wecom", List.of(Field.webhookUrl()), List.of()),

    /** 企业微信应用消息. */
    WECOM_APP(
            "企业微信应用消息",
            "wecom",
            List.of(
                    Field.text(NotificationSecretKeys.CORP_ID, "企业 ID", "ww..."),
                    Field.text(NotificationSecretKeys.AGENT_ID, "应用 AgentId", "1000002"),
                    Field.secret(NotificationSecretKeys.CORP_SECRET, "应用 Secret")),
            List.of()),

    /** 钉钉群机器人. */
    DINGTALK_BOT(
            "钉钉机器人",
            "dingtalk",
            List.of(Field.webhookUrl()),
            List.of(Field.secret(NotificationSecretKeys.SIGN_SECRET, "加签密钥"))),

    /** 飞书群机器人. */
    LARK_BOT(
            "飞书机器人",
            "lark",
            List.of(Field.webhookUrl()),
            List.of(Field.secret(NotificationSecretKeys.SIGN_SECRET, "加签密钥"))),

    /** SMTP 邮箱. */
    SMTP(
            "SMTP 邮箱",
            "email",
            List.of(
                    Field.text(NotificationSecretKeys.SMTP_HOST, "SMTP 服务器", "smtp.example.com"),
                    Field.number(NotificationSecretKeys.SMTP_PORT, "端口", "465"),
                    Field.text(NotificationSecretKeys.SMTP_USERNAME, "账号", "alerts@example.com"),
                    Field.secret(NotificationSecretKeys.SMTP_PASSWORD, "口令")),
            List.of(
                    Field.optionalText(NotificationSecretKeys.SMTP_FROM, "发件人(可选)", "留空同账号"),
                    Field.bool(NotificationSecretKeys.SMTP_USE_TLS, "启用 TLS"))),

    /** HTTP Bearer 认证的通用出口. */
    HTTP_BEARER(
            "HTTP Bearer",
            "generic",
            List.of(
                    Field.webhookUrl(),
                    Field.secret(NotificationSecretKeys.HTTP_BEARER_TOKEN, "Bearer Token")),
            List.of()),

    /** HTTP Basic 认证的通用出口. */
    HTTP_BASIC(
            "HTTP Basic",
            "generic",
            List.of(
                    Field.webhookUrl(),
                    Field.text(NotificationSecretKeys.HTTP_BASIC_USERNAME, "用户名", ""),
                    Field.secret(NotificationSecretKeys.HTTP_BASIC_PASSWORD, "口令")),
            List.of());

    private final String displayName;
    private final String channel;
    private final List<Field> requiredFields;
    private final List<Field> optionalFields;

    NotificationChannelType(
            String displayName,
            String channel,
            List<Field> requiredFields,
            List<Field> optionalFields) {
        this.displayName = displayName;
        this.channel = channel;
        this.requiredFields = requiredFields;
        this.optionalFields = optionalFields;
    }

    /**
     * 前端展示名.
     *
     * @return 展示名
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 渠道码 -- 与 V25 渠道开关里的值对应,站点关掉某个渠道时这一类凭据整体不可选.
     *
     * @return 渠道码
     */
    public String channel() {
        return channel;
    }

    /**
     * 必填字段.
     *
     * @return 必填字段列表
     */
    public List<Field> requiredFields() {
        return requiredFields;
    }

    /**
     * 可选字段.
     *
     * @return 可选字段列表
     */
    public List<Field> optionalFields() {
        return optionalFields;
    }

    /**
     * 全部字段,必填在前.
     *
     * @return 字段列表
     */
    public List<Field> allFields() {
        // required 由字段落在哪张列表决定,而非各工厂方法各自硬编 —— 同一个 secret() 出来的加签密钥,
        // 放在 optionalFields 里就该是可选。否则钉钉/飞书的「加签密钥(可选)」会带上必填星号。
        List<Field> out = new ArrayList<>();
        requiredFields.forEach(f -> out.add(f.withRequired(true)));
        optionalFields.forEach(f -> out.add(f.withRequired(false)));
        return out;
    }

    /**
     * 按名称解析类型.
     *
     * @param name 类型名
     * @return 匹配的类型;认不出时为空
     */
    public static Optional<NotificationChannelType> of(String name) {
        if (name == null) return Optional.empty();
        return Arrays.stream(values()).filter(t -> t.name().equals(name)).findFirst();
    }

    /**
     * 秘密字段的表单元数据 -- 前端据此渲染控件,不硬编码任何厂商字段.
     *
     * @param key 字段键,与秘密 JSON 里的键名一致
     * @param label 标签
     * @param inputType 控件类型: text / password / number / boolean
     * @param placeholder 占位提示
     * @param required 是否必填
     */
    public record Field(
            String key, String label, String inputType, String placeholder, boolean required) {

        static Field webhookUrl() {
            return new Field(
                    NotificationSecretKeys.WEBHOOK_URL,
                    "Webhook 地址",
                    "password",
                    "https://...",
                    true);
        }

        static Field text(String key, String label, String placeholder) {
            return new Field(key, label, "text", placeholder, true);
        }

        static Field optionalText(String key, String label, String placeholder) {
            return new Field(key, label, "text", placeholder, false);
        }

        static Field secret(String key, String label) {
            return new Field(key, label, "password", "", true);
        }

        static Field number(String key, String label, String placeholder) {
            return new Field(key, label, "number", placeholder, true);
        }

        static Field bool(String key, String label) {
            return new Field(key, label, "boolean", "", false);
        }

        /**
         * 复制一个只改 required 的副本 -- allFields() 据字段所在列表统一归位必填与否.
         *
         * @param req 是否必填
         * @return 副本
         */
        Field withRequired(boolean req) {
            return new Field(key, label, inputType, placeholder, req);
        }
    }
}
