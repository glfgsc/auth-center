package com.auth.center.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * IP 地理归类服务 —— 内网/公网分类 + 可插拔精确地理位置.
 *
 * {@link #classify} 判定内网(私有/环回/链路本地地址)vs 公网(前缀法,不做 DNS,快)。 {@link #locate} 给内网返回 {@code null}(由
 * {@code ipClass=INTERNAL} 展示「内网」),给公网走可选 {@link GeoIpProvider}(平台注册离线 GeoIP 库时解析到「城市,国家」),无则返回
 * {@code null} (前端展示「公网」)。契合生产无外网:不硬依赖任何外部 GeoIP 服务。
 */
@Component
public class IpGeoService {

    /** IP 归类:内网。 */
    public static final String CLASS_INTERNAL = "INTERNAL";

    /** IP 归类:公网。 */
    public static final String CLASS_PUBLIC = "PUBLIC";

    /** 可选精确地理位置解析器(平台提供离线 GeoIP 库时注册)。 */
    private final ObjectProvider<GeoIpProvider> geoIpProvider;

    /**
     * 构造注入。
     *
     * @param geoIpProvider 可选 GeoIP 解析器(0 或 1 个)
     */
    public IpGeoService(ObjectProvider<GeoIpProvider> geoIpProvider) {
        this.geoIpProvider = geoIpProvider;
    }

    /**
     * 归类 IP 为内网 / 公网。
     *
     * @param ip 客户端 IP
     * @return {@link #CLASS_INTERNAL} 或 {@link #CLASS_PUBLIC}
     */
    public String classify(String ip) {
        return isInternal(ip) ? CLASS_INTERNAL : CLASS_PUBLIC;
    }

    /**
     * 解析精确地理位置。
     *
     * 内网返回 {@code null}(由归类展示「内网」);公网走可插拔 {@link GeoIpProvider},无则 {@code null}。
     *
     * @param ip 客户端 IP
     * @return 「城市,国家」展示串;内网或无法解析返回 {@code null}
     */
    public String locate(String ip) {
        if (isInternal(ip)) {
            return null;
        }
        GeoIpProvider provider = geoIpProvider.getIfAvailable();
        return provider != null ? provider.locate(ip) : null;
    }

    /**
     * 判定 IP 是否为内网(私有 / 环回 / 链路本地 / 非 IP 主机名)。
     *
     * 前缀法,不触发 DNS。IPv4-mapped IPv6({@code ::ffff:x.x.x.x})先剥前缀。空 IP 保守视为内网。
     *
     * @param ip 客户端 IP
     * @return 内网返回 {@code true}
     */
    private boolean isInternal(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        String s = ip.trim().toLowerCase();
        // IPv4-mapped IPv6:剥 ::ffff: 前缀取内嵌 IPv4
        if (s.startsWith("::ffff:")) {
            s = s.substring("::ffff:".length());
        }
        if (s.equals("::1") || s.equals("localhost")) {
            return true;
        }
        // IPv6 私有/链路本地:唯一本地地址(fc00::/7 → fc/fd)、链路本地(fe80::/10)
        if (s.startsWith("fc")
                || s.startsWith("fd")
                || s.startsWith("fe8")
                || s.startsWith("fe9")
                || s.startsWith("fea")
                || s.startsWith("feb")) {
            return true;
        }
        // IPv4 私有 / 环回 / 链路本地
        if (s.startsWith("10.")
                || s.startsWith("192.168.")
                || s.startsWith("127.")
                || s.startsWith("169.254.")) {
            return true;
        }
        if (s.startsWith("172.")) {
            int second = secondOctet(s);
            return second >= 16 && second <= 31;
        }
        // 非 IP 字面量(如 host.docker.internal 等本地基础设施主机名)视为内网
        return !s.matches("[0-9a-f:.]+");
    }

    /**
     * 取 IPv4 第二段(如 172.20.x.x → 20);解析失败返回 -1。
     *
     * @param ipv4 IPv4 字符串
     * @return 第二段数值,失败 -1
     */
    private int secondOctet(String ipv4) {
        int first = ipv4.indexOf('.');
        int second = ipv4.indexOf('.', first + 1);
        if (first < 0 || second < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(ipv4.substring(first + 1, second));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
