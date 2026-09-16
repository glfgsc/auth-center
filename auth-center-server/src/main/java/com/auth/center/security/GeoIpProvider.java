package com.auth.center.security;

/**
 * 公网 IP → 地理位置解析器 —— 可插拔钩子.
 *
 * 生产环境无外网,精确城市/国家需离线 GeoIP 库。本接口是插拔点:平台提供离线 GeoIP 库时,注册一个 {@code @Component} 实现即可让 {@link
 * IpGeoService} 把公网 IP 解析到「城市,国家」;未注册时公网 IP 仅标记为「公网」(不解析具体地区)。避免把外部 GeoIP API 硬编进主流程。
 */
public interface GeoIpProvider {

    /**
     * 解析公网 IP 的地理位置。
     *
     * @param ip 公网 IP(IPv4/IPv6 字面量)
     * @return 「城市,国家」等展示串;无法解析返回 {@code null}
     */
    String locate(String ip);
}
