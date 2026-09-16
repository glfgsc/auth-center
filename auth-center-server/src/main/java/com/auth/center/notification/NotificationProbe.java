package com.auth.center.notification;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 通知渠道连通性探测 -- 向上游真发一次,用来回答"这条凭据现在还有效吗".
 *
 * 结果消息里绝不能出现完整地址: webhook URL 本身就是凭据,而它会经由测试结果落进 {@code last_test_message} 列、再显示到管理页面上.
 * 一律经 {@link #safeHost} 只留 {@code scheme://host}.
 *
 * SMTP 走真正的 {@code Transport.connect} 认证 -- 只探测 TCP 可达等于没测口令,而"测试通过"却发不出信比没有测试按钮更糟.
 */
@Component
public class NotificationProbe {

    private static final Logger log = LoggerFactory.getLogger(NotificationProbe.class);

    /** 探测超时 -- 出网调用必须有闸,否则一个不响应的上游能把管理页面挂死. */
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    /** 各厂商的探测报文 -- 内容是给收到的人看的,说明这只是一次配置验证. */
    private static final String BODY_WECOM =
            "{\"msgtype\":\"text\",\"text\":{\"content\":\"[连通性测试] 通知渠道配置成功。\"}}";

    private static final String BODY_DINGTALK =
            "{\"msgtype\":\"text\",\"text\":{\"content\":\"[连通性测试] 通知渠道配置成功。\"}}";

    private static final String BODY_LARK_TEMPLATE =
            "{\"msg_type\":\"text\",\"content\":{\"text\":\"[连通性测试] 通知渠道配置成功。\"}%s}";

    private static final String BODY_GENERIC =
            "{\"event\":\"connectivity_test\",\"message\":\"[连通性测试] 通知渠道配置成功。\"}";

    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    /**
     * 按类型分派探测.
     *
     * @param credType 渠道类型名
     * @param secret 解密后的秘密键值对
     * @return 探测结果
     */
    public Result probe(String credType, Map<String, Object> secret) {
        if (secret == null || secret.isEmpty()) {
            return Result.fail("凭据内容为空");
        }
        NotificationChannelType type = NotificationChannelType.of(credType).orElse(null);
        if (type == null) {
            return Result.fail("未知的渠道类型: " + credType);
        }
        try {
            return switch (type) {
                case WECOM_BOT ->
                        postWebhook(
                                str(secret, NotificationSecretKeys.WEBHOOK_URL), BODY_WECOM, null);
                case DINGTALK_BOT -> probeDingtalk(secret);
                case LARK_BOT -> probeLark(secret);
                case HTTP_BEARER ->
                        postWebhook(
                                str(secret, NotificationSecretKeys.WEBHOOK_URL),
                                BODY_GENERIC,
                                "Bearer " + str(secret, NotificationSecretKeys.HTTP_BEARER_TOKEN));
                case HTTP_BASIC ->
                        postWebhook(
                                str(secret, NotificationSecretKeys.WEBHOOK_URL),
                                BODY_GENERIC,
                                basicAuth(secret));
                case WECOM_APP -> probeWecomApp(secret);
                case SMTP -> probeSmtp(secret);
            };
        } catch (Exception e) {
            log.warn("[NotifProbe] probe failed for type={}: {}", credType, e.toString());
            return Result.fail("探测失败: " + e.getClass().getSimpleName());
        }
    }

    private Result probeDingtalk(Map<String, Object> secret) {
        String url = str(secret, NotificationSecretKeys.WEBHOOK_URL);
        String sign = str(secret, NotificationSecretKeys.SIGN_SECRET);
        if (sign != null && !sign.isBlank()) {
            url = appendDingtalkSignature(url, sign);
        }
        return postWebhook(url, BODY_DINGTALK, null);
    }

    private Result probeLark(Map<String, Object> secret) {
        String url = str(secret, NotificationSecretKeys.WEBHOOK_URL);
        String sign = str(secret, NotificationSecretKeys.SIGN_SECRET);
        String extra = "";
        if (sign != null && !sign.isBlank()) {
            long ts = System.currentTimeMillis() / 1000;
            extra = ",\"timestamp\":\"" + ts + "\",\"sign\":\"" + larkSign(ts, sign) + "\"";
        }
        return postWebhook(url, String.format(BODY_LARK_TEMPLATE, extra), null);
    }

    /** 企业微信应用消息:换取 access_token 即可验证 corpid + secret,不必真发一条给谁. */
    private Result probeWecomApp(Map<String, Object> secret) {
        String corpId = str(secret, NotificationSecretKeys.CORP_ID);
        String corpSecret = str(secret, NotificationSecretKeys.CORP_SECRET);
        if (corpId == null || corpSecret == null) {
            return Result.fail("缺少企业 ID 或应用 Secret");
        }
        String url =
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                        + URLEncoder.encode(corpId, StandardCharsets.UTF_8)
                        + "&corpsecret="
                        + URLEncoder.encode(corpSecret, StandardCharsets.UTF_8);
        try {
            HttpResponse<String> res =
                    http.send(
                            HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return interpretVendorBody(res.statusCode(), res.body(), "qyapi.weixin.qq.com");
        } catch (Exception e) {
            return Result.fail("连接企业微信失败: " + e.getClass().getSimpleName());
        }
    }

    private Result probeSmtp(Map<String, Object> secret) {
        String host = str(secret, NotificationSecretKeys.SMTP_HOST);
        String username = str(secret, NotificationSecretKeys.SMTP_USERNAME);
        String password = str(secret, NotificationSecretKeys.SMTP_PASSWORD);
        Integer port = intOf(secret.get(NotificationSecretKeys.SMTP_PORT));
        if (host == null || port == null || username == null || password == null) {
            return Result.fail("SMTP 主机、端口、账号、口令均为必填");
        }
        boolean useTls = boolOf(secret.get(NotificationSecretKeys.SMTP_USE_TLS));

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT.toMillis()));
        props.put("mail.smtp.timeout", String.valueOf(TIMEOUT.toMillis()));
        // 465 是隐式 SSL,587 / 25 走 STARTTLS —— 两者的开关不是同一个,配错表现成握手期卡死。
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        try (Transport transport = Session.getInstance(props).getTransport("smtp")) {
            transport.connect(host, port, username, password);
            return Result.ok("SMTP 认证成功 (" + host + ":" + port + ")");
        } catch (Exception e) {
            // 消息里带 host:port 无妨(不是秘密),但口令绝不能进。
            return Result.fail("SMTP 认证失败 (" + host + ":" + port + "): " + rootMessage(e));
        }
    }

    private Result postWebhook(String url, String body, String authHeader) {
        if (url == null || url.isBlank()) {
            return Result.fail("缺少 Webhook 地址");
        }
        String host = safeHost(url);
        try {
            HttpRequest.Builder req =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/json; charset=utf-8")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            body, StandardCharsets.UTF_8));
            if (authHeader != null) {
                req.header("Authorization", authHeader);
            }
            HttpResponse<String> res =
                    http.send(
                            req.build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return interpretVendorBody(res.statusCode(), res.body(), host);
        } catch (Exception e) {
            // 异常消息里内嵌完整 URI 是这条路上最容易泄密的地方 —— 只保留异常类型与 host。
            return Result.fail("连接 " + host + " 失败: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 厂商都用 HTTP 200 + 报文里的 errcode 表达失败,只看状态码会把"密钥错误"当成成功.
     *
     * @param status HTTP 状态码
     * @param body 响应报文
     * @param host 已脱敏的主机名
     * @return 探测结果
     */
    private Result interpretVendorBody(int status, String body, String host) {
        if (status < 200 || status >= 300) {
            return Result.fail(host + " 返回 HTTP " + status);
        }
        if (body == null || body.isBlank()) {
            return Result.ok("已送达 " + host);
        }
        // errcode / code 非 0 即失败(企微、钉钉、飞书同构);StatusCode 是飞书新版字段。
        String compact = body.replace(" ", "");
        for (String field : new String[] {"\"errcode\":", "\"code\":", "\"StatusCode\":"}) {
            int at = compact.indexOf(field);
            if (at < 0) continue;
            String tail = compact.substring(at + field.length());
            int end = 0;
            while (end < tail.length()
                    && (Character.isDigit(tail.charAt(end)) || tail.charAt(end) == '-')) {
                end++;
            }
            if (end == 0) continue;
            if (!"0".equals(tail.substring(0, end))) {
                return Result.fail(host + " 拒绝: " + truncate(body));
            }
            return Result.ok("已送达 " + host);
        }
        return Result.ok("已送达 " + host);
    }

    /**
     * 只保留 {@code scheme://host} -- 路径与查询串里藏着令牌.
     *
     * @param url 原始地址
     * @return 脱敏后的主机标识
     */
    static String safeHost(String url) {
        try {
            URI u = URI.create(url);
            return u.getScheme() + "://" + u.getHost();
        } catch (Exception e) {
            return "(地址无法解析)";
        }
    }

    /**
     * 钉钉加签: {@code HmacSHA256(timestamp + "\n" + secret)}, 密钥也是 secret 本身.
     *
     * @param webhookUrl 原始 webhook 地址
     * @param secret 加签密钥
     * @return 追加 timestamp 与 sign 的完整地址
     */
    static String appendDingtalkSignature(String webhookUrl, String secret) {
        try {
            long ts = System.currentTimeMillis();
            String stringToSign = ts + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign =
                    URLEncoder.encode(
                            Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
            String sep = webhookUrl.contains("?") ? "&" : "?";
            return webhookUrl + sep + "timestamp=" + ts + "&sign=" + sign;
        } catch (Exception e) {
            log.warn("[NotifProbe] dingtalk signature failed: {}", e.getMessage());
            return webhookUrl;
        }
    }

    /**
     * 飞书加签:以 {@code timestamp + "\n" + secret} 为密钥对空串做 HmacSHA256, 与钉钉正好相反.
     *
     * @param ts 秒级时间戳
     * @param secret 加签密钥
     * @return base64 签名
     */
    static String larkSign(long ts, String secret) {
        try {
            String stringToSign = ts + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(new byte[] {}));
        } catch (Exception e) {
            log.warn("[NotifProbe] lark signature failed: {}", e.getMessage());
            return "";
        }
    }

    private static String basicAuth(Map<String, Object> secret) {
        String user = str(secret, NotificationSecretKeys.HTTP_BASIC_USERNAME);
        String pass = str(secret, NotificationSecretKeys.HTTP_BASIC_PASSWORD);
        String raw = (user == null ? "" : user) + ":" + (pass == null ? "" : pass);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String str(Map<String, Object> secret, String key) {
        Object v = secret.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Integer intOf(Object v) {
        if (v == null) return null;
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean boolOf(Object v) {
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : truncate(msg);
    }

    private static String truncate(String s) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= 180 ? one : one.substring(0, 180) + "...";
    }

    /**
     * 探测结果.
     *
     * @param ok 是否成功
     * @param message 结果说明,已确保不含完整地址
     */
    public record Result(boolean ok, String message) {

        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
