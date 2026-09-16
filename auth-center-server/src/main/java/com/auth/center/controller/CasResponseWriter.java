package com.auth.center.controller;

import com.auth.center.cas.ServiceTicket;
import com.auth.center.entity.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * CAS 协议响应的渲染 —— 票据校验的成功 / 失败 XML、JSON 响应外壳与用户信息映射。
 *
 * XML 一律经 escapeXml 出:用户名与属性值来自目录服务,含 {@code &} 或 {@code <} 时不转义会让整份响应对客户端解析失败,而失败长得像「票据无效」。
 */
final class CasResponseWriter {

    /** Accept 请求头名称 */
    private static final String HEADER_ACCEPT = "Accept";

    /** JSON 媒体类型前缀 */
    private static final String JSON_MEDIA_PREFIX = "application/json";

    /** CAS XML 命名空间前缀 */
    private static final String CAS_NS = "http://www.yale.edu/tp/cas";

    private CasResponseWriter() {}

    /**
     * 判断请求是否期望 JSON 响应.
     *
     * @param request HTTP 请求
     * @return {@code true} 表示客户端期望 JSON 响应
     */
    static boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader(HEADER_ACCEPT);
        return accept != null && accept.contains(JSON_MEDIA_PREFIX);
    }

    /**
     * 包装 Map 为 JSON ResponseEntity（用于 loginPage 返回 Object 时的 JSON 路径）.
     *
     * @param body 响应体 Map
     * @return ResponseEntity 实例
     */
    @ResponseBody
    static ResponseEntity<Map<String, Object>> buildJsonResponse(Map<String, Object> body) {
        return ResponseEntity.ok(body);
    }

    /**
     * 构建 CAS 验票成功的 XML 响应.
     *
     * @param st 已验证的 Service Ticket
     * @param jwt 签发的 JWT 令牌
     * @return CAS XML 字符串
     */
    static String buildCasSuccessXml(ServiceTicket st, String jwt) {
        StringBuilder sb = new StringBuilder();
        sb.append("<cas:serviceResponse xmlns:cas=\"").append(CAS_NS).append("\">\n");
        sb.append("  <cas:authenticationSuccess>\n");
        sb.append("    <cas:user>").append(escapeXml(st.getUsername())).append("</cas:user>\n");
        sb.append("    <cas:attributes>\n");
        sb.append("      <cas:userId>").append(st.getUserId()).append("</cas:userId>\n");
        sb.append("      <cas:permissionSet>")
                .append(escapeXml(st.getPermissionSet()))
                .append("</cas:permissionSet>\n");
        sb.append("      <cas:capabilities>")
                .append(escapeXml(st.getCapabilities()))
                .append("</cas:capabilities>\n");
        sb.append("      <cas:token>").append(jwt).append("</cas:token>\n");
        sb.append("    </cas:attributes>\n");
        sb.append("  </cas:authenticationSuccess>\n");
        sb.append("</cas:serviceResponse>");
        return sb.toString();
    }

    /**
     * 构建 CAS 验票失败的 XML 响应.
     *
     * @param code 错误码，如 "INVALID_TICKET"
     * @param message 错误描述
     * @return CAS XML 字符串
     */
    static String buildCasFailureXml(String code, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<cas:serviceResponse xmlns:cas=\"").append(CAS_NS).append("\">\n");
        sb.append("  <cas:authenticationFailure code=\"").append(escapeXml(code)).append("\">\n");
        sb.append("    ").append(escapeXml(message)).append("\n");
        sb.append("  </cas:authenticationFailure>\n");
        sb.append("</cas:serviceResponse>");
        return sb.toString();
    }

    /**
     * 将用户实体转换为安全的 Map（排除密码）.
     *
     * @param user 用户实体
     * @return 包含 id、username、nickname、avatar 的 Map
     */
    static Map<String, Object> buildUserMap(AuthUser user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        return map;
    }

    /**
     * 构建错误响应 Map.
     *
     * @param message 错误消息
     * @return 包含 error 字段的 Map
     */
    static Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("error", message);
        return map;
    }

    /**
     * 转义 XML 特殊字符.
     *
     * @param input 原始字符串
     * @return 转义后的字符串，null 安全
     */
    static String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
