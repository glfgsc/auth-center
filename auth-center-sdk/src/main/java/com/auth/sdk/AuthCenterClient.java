package com.auth.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Auth Center REST 客户端 -- 与认证中心 API 交互.
 *
 * <p>提供以下能力:
 * <ul>
 *     <li>CAS ticket 验证 ({@code /cas/serviceValidate})</li>
 *     <li>当前用户信息查询 ({@code /api/auth/info})</li>
 *     <li>当前用户权限集查询 ({@code /api/auth/permission-set})</li>
 * </ul>
 *
 * <p>内部使用 JDK {@link HttpClient}, 不依赖 Spring RestTemplate.
 * 线程安全: 可在多线程环境中共享使用.
 */
public class AuthCenterClient {

    private static final Logger log = LoggerFactory.getLogger(AuthCenterClient.class);

    /** HTTP 请求超时时间 (秒) */
    private static final int HTTP_TIMEOUT_SECONDS = 10;

    /** HTTP 200 状态码 */
    private static final int HTTP_OK = 200;

    /** Authorization Header 名称 */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /** CAS ticket 验证端点路径 */
    private static final String CAS_VALIDATE_PATH = "/cas/serviceValidate";

    /** 用户信息查询端点路径 */
    private static final String USER_INFO_PATH = "/api/auth/info";

    /** 权限集查询端点路径 */
    private static final String PERMISSION_SET_PATH = "/api/auth/permission-set";

    /** CAS XML 命名空间 URI */
    private static final String CAS_NAMESPACE = "http://www.yale.edu/tp/cas";

    /** CAS XML: 认证成功元素 */
    private static final String CAS_AUTH_SUCCESS = "authenticationSuccess";

    /** CAS XML: 认证失败元素 */
    private static final String CAS_AUTH_FAILURE = "authenticationFailure";

    /** CAS XML: 用户名元素 */
    private static final String CAS_USER = "user";

    /** CAS XML: 属性容器元素 */
    private static final String CAS_ATTRIBUTES = "attributes";

    /** CAS 属性: 用户 ID */
    private static final String CAS_ATTR_USER_ID = "userId";

    /** CAS 属性: 权限集 */
    private static final String CAS_ATTR_PERMISSION_SET = "permissionSet";

    /** CAS 属性: 能力列表 */
    private static final String CAS_ATTR_CAPABILITIES = "capabilities";

    /** CAS 属性: JWT Token */
    private static final String CAS_ATTR_TOKEN = "token";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造 Auth Center REST 客户端.
     *
     * @param baseUrl Auth Center 根 URL, 如 {@code http://auth-center:8090}, 末尾不需要斜杠
     */
    public AuthCenterClient(String baseUrl) {
        // 去除末尾斜杠
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 验证 CAS ticket.
     *
     * <p>向 Auth Center 的 {@code /cas/serviceValidate} 端点发送请求,
     * 解析 CAS XML 响应判断 ticket 是否有效.
     *
     * @param ticket     CAS ticket 字符串
     * @param serviceUrl 服务回调 URL (即 CAS 协议中的 service 参数)
     * @return 验证结果, 包含成功/失败状态及用户信息
     * @throws RuntimeException 网络请求或 XML 解析失败时抛出
     */
    public CasValidationResult validateCasTicket(String ticket, String serviceUrl) {
        String encodedTicket = URLEncoder.encode(ticket, StandardCharsets.UTF_8);
        String encodedService = URLEncoder.encode(serviceUrl, StandardCharsets.UTF_8);
        String url = baseUrl + CAS_VALIDATE_PATH
                + "?ticket=" + encodedTicket
                + "&service=" + encodedService;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HTTP_OK) {
                CasValidationResult result = new CasValidationResult();
                result.setSuccess(false);
                result.setFailureCode("HTTP_ERROR");
                result.setFailureMessage("CAS 验证请求失败, HTTP " + response.statusCode());
                return result;
            }

            return parseCasResponse(response.body());
        } catch (Exception e) {
            log.warn("CAS ticket 验证异常: {}", e.getMessage());
            throw new RuntimeException("CAS ticket 验证失败", e);
        }
    }

    /**
     * 查询当前用户信息.
     *
     * @param bearerToken JWT Bearer Token (不含 "Bearer " 前缀)
     * @return 用户信息 Map (字段依赖 Auth Center 接口定义)
     * @throws RuntimeException 请求失败时抛出
     */
    public Map<String, Object> getUserInfo(String bearerToken) {
        return get(USER_INFO_PATH, "Bearer " + bearerToken,
                new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 查询当前用户的权限集信息.
     *
     * @param bearerToken JWT Bearer Token (不含 "Bearer " 前缀)
     * @return 权限集信息 Map (字段依赖 Auth Center 接口定义)
     * @throws RuntimeException 请求失败时抛出
     */
    public Map<String, Object> getPermissionInfo(String bearerToken) {
        return get(PERMISSION_SET_PATH, "Bearer " + bearerToken,
                new TypeReference<Map<String, Object>>() {});
    }

    // ======================== 内部方法 ========================

    /**
     * 发送 GET 请求并反序列化响应体.
     *
     * @param path       相对路径 (如 {@code /api/auth/info})
     * @param authHeader Authorization Header 值 (如 {@code Bearer xxx})
     * @param typeRef    目标类型引用
     * @param <T>        响应类型
     * @return 反序列化后的响应对象
     * @throws RuntimeException 请求失败或反序列化失败时抛出
     */
    private <T> T get(String path, String authHeader, TypeReference<T> typeRef) {
        String url = baseUrl + path;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .GET();

            if (authHeader != null && !authHeader.isEmpty()) {
                builder.header(HEADER_AUTHORIZATION, authHeader);
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HTTP_OK) {
                throw new RuntimeException(
                        "Auth Center 请求失败, HTTP " + response.statusCode()
                                + ", URL: " + url);
            }

            return objectMapper.readValue(response.body(), typeRef);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Auth Center 请求异常, URL: " + url, e);
        }
    }

    /**
     * 解析 CAS XML 响应.
     *
     * <p>使用安全配置的 {@link DocumentBuilderFactory} (禁用外部实体, 防止 XXE 攻击).
     *
     * @param xml CAS XML 响应字符串
     * @return 解析后的验证结果
     * @throws Exception XML 解析失败时抛出
     */
    private CasValidationResult parseCasResponse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE 防护: 禁用外部实体和 DTD
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setNamespaceAware(true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        CasValidationResult result = new CasValidationResult();

        // 检查认证成功
        NodeList successNodes = doc.getElementsByTagNameNS(CAS_NAMESPACE, CAS_AUTH_SUCCESS);
        if (successNodes.getLength() > 0) {
            Element successElem = (Element) successNodes.item(0);
            result.setSuccess(true);

            // 提取用户名
            result.setUsername(getElementText(successElem, CAS_NAMESPACE, CAS_USER));

            // 提取属性
            NodeList attrNodes = successElem.getElementsByTagNameNS(CAS_NAMESPACE, CAS_ATTRIBUTES);
            if (attrNodes.getLength() > 0) {
                Element attrsElem = (Element) attrNodes.item(0);
                result.setUserId(parseLongAttribute(attrsElem, CAS_NAMESPACE, CAS_ATTR_USER_ID));
                result.setPermissionSet(
                        getElementText(attrsElem, CAS_NAMESPACE, CAS_ATTR_PERMISSION_SET));
                result.setCapabilities(
                        getElementText(attrsElem, CAS_NAMESPACE, CAS_ATTR_CAPABILITIES));
                result.setToken(getElementText(attrsElem, CAS_NAMESPACE, CAS_ATTR_TOKEN));
            }
            return result;
        }

        // 检查认证失败
        NodeList failureNodes = doc.getElementsByTagNameNS(CAS_NAMESPACE, CAS_AUTH_FAILURE);
        if (failureNodes.getLength() > 0) {
            Element failureElem = (Element) failureNodes.item(0);
            result.setSuccess(false);
            result.setFailureCode(failureElem.getAttribute("code"));
            result.setFailureMessage(failureElem.getTextContent().trim());
            return result;
        }

        // 未知响应格式
        result.setSuccess(false);
        result.setFailureCode("UNKNOWN_RESPONSE");
        result.setFailureMessage("无法解析 CAS 响应");
        return result;
    }

    /**
     * 从 XML 元素中提取指定子元素的文本内容.
     *
     * @param parent        父元素
     * @param namespaceUri  命名空间 URI
     * @param localName     子元素本地名称
     * @return 文本内容, 未找到时返回 null
     */
    private String getElementText(Element parent, String namespaceUri, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespaceUri, localName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    /**
     * 从 XML 元素中提取指定子元素的 Long 值.
     *
     * @param parent        父元素
     * @param namespaceUri  命名空间 URI
     * @param localName     子元素本地名称
     * @return Long 值, 未找到或格式错误时返回 null
     */
    private Long parseLongAttribute(Element parent, String namespaceUri, String localName) {
        String text = getElementText(parent, namespaceUri, localName);
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            log.warn("CAS 属性 {} 值 '{}' 无法解析为 Long", localName, text);
            return null;
        }
    }

    // ======================== 内部类: CAS 验证结果 ========================

    /**
     * CAS ticket 验证结果.
     *
     * <p>包含验证成功时的用户信息, 或验证失败时的错误码和错误消息.
     */
    public static class CasValidationResult {

        /** 验证是否成功 */
        private boolean success;

        /** 用户名 (验证成功时) */
        private String username;

        /** 用户 ID (验证成功时) */
        private Long userId;

        /** 权限集名称 (验证成功时) */
        private String permissionSet;

        /** 能力列表, 逗号分隔 (验证成功时) */
        private String capabilities;

        /** Auth Center 签发的 JWT Token (验证成功时) */
        private String token;

        /** 失败错误码 (验证失败时) */
        private String failureCode;

        /** 失败错误消息 (验证失败时) */
        private String failureMessage;

        /**
         * 获取验证是否成功.
         *
         * @return {@code true} 表示验证通过
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * 设置验证是否成功.
         *
         * @param success 验证结果
         */
        public void setSuccess(boolean success) {
            this.success = success;
        }

        /**
         * 获取用户名.
         *
         * @return 用户名, 验证失败时为 null
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置用户名.
         *
         * @param username 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 获取用户 ID.
         *
         * @return 用户 ID, 验证失败时为 null
         */
        public Long getUserId() {
            return userId;
        }

        /**
         * 设置用户 ID.
         *
         * @param userId 用户 ID
         */
        public void setUserId(Long userId) {
            this.userId = userId;
        }

        /**
         * 获取权限集名称.
         *
         * @return 权限集名称, 验证失败时为 null
         */
        public String getPermissionSet() {
            return permissionSet;
        }

        /**
         * 设置权限集名称.
         *
         * @param permissionSet 权限集名称
         */
        public void setPermissionSet(String permissionSet) {
            this.permissionSet = permissionSet;
        }

        /**
         * 获取能力列表 (逗号分隔).
         *
         * @return 能力列表字符串, 验证失败时为 null
         */
        public String getCapabilities() {
            return capabilities;
        }

        /**
         * 设置能力列表.
         *
         * @param capabilities 逗号分隔的能力字符串
         */
        public void setCapabilities(String capabilities) {
            this.capabilities = capabilities;
        }

        /**
         * 获取 Auth Center 签发的 JWT Token.
         *
         * @return JWT Token 字符串, 验证失败时为 null
         */
        public String getToken() {
            return token;
        }

        /**
         * 设置 JWT Token.
         *
         * @param token JWT Token 字符串
         */
        public void setToken(String token) {
            this.token = token;
        }

        /**
         * 获取失败错误码.
         *
         * @return 错误码, 验证成功时为 null
         */
        public String getFailureCode() {
            return failureCode;
        }

        /**
         * 设置失败错误码.
         *
         * @param failureCode 错误码
         */
        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        /**
         * 获取失败错误消息.
         *
         * @return 错误消息, 验证成功时为 null
         */
        public String getFailureMessage() {
            return failureMessage;
        }

        /**
         * 设置失败错误消息.
         *
         * @param failureMessage 错误消息
         */
        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }
    }
}
