package com.auth.center.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 刷新令牌请求 DTO.
 *
 * 替代原 {@code Map<String, String>} 参数，对 refresh token 做非空和长度约束。
 */
public class RefreshTokenRequest {

    /** Refresh Token 字符串，不可为空 */
    @NotBlank(message = "refreshToken 不能为空")
    @Size(max = 2000, message = "refreshToken 长度不能超过 2000")
    private String refreshToken;

    /**
     * 获取 refresh token.
     *
     * @return refresh token
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * 设置 refresh token.
     *
     * @param refreshToken refresh token
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
