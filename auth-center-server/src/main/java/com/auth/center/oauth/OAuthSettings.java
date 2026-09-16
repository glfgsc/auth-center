package com.auth.center.oauth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** Auth Center 中可由管理员维护的 OAuth 服务配置；单例记录 id 固定为 1。 */
@TableName("auth_oauth_settings")
public class OAuthSettings {
    @TableId private Long id;
    private String issuer;
    private String mcpResourceUrl;
    private String scopes;
    private Integer accessTokenTtlSeconds;
    private Integer refreshTokenTtlSeconds;
    private Integer authorizationCodeTtlSeconds;
    private String resourceName;
    private String resourceDocumentation;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getMcpResourceUrl() {
        return mcpResourceUrl;
    }

    public void setMcpResourceUrl(String mcpResourceUrl) {
        this.mcpResourceUrl = mcpResourceUrl;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public Integer getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(Integer value) {
        this.accessTokenTtlSeconds = value;
    }

    public Integer getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(Integer value) {
        this.refreshTokenTtlSeconds = value;
    }

    public Integer getAuthorizationCodeTtlSeconds() {
        return authorizationCodeTtlSeconds;
    }

    public void setAuthorizationCodeTtlSeconds(Integer value) {
        this.authorizationCodeTtlSeconds = value;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getResourceDocumentation() {
        return resourceDocumentation;
    }

    public void setResourceDocumentation(String resourceDocumentation) {
        this.resourceDocumentation = resourceDocumentation;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
