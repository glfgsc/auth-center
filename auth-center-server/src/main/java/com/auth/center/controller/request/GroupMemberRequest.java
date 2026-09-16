package com.auth.center.controller.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 用户组成员操作请求 DTO.
 *
 * 替代原 {@code Map<String, Object>} + 强制类型转换，提供类型安全的用户 ID 列表约束。
 */
public class GroupMemberRequest {

    /** 用户 ID 列表，不可为空，最多 1000 个 */
    @NotEmpty(message = "userIds 不能为空")
    @Size(max = 1000, message = "单次操作用户数不能超过 1000")
    private List<@NotNull(message = "userId 不能为 null") @Positive(message = "userId 必须为正数") Long>
            userIds;

    /**
     * 获取用户 ID 列表.
     *
     * @return 用户 ID 列表
     */
    public List<Long> getUserIds() {
        return userIds;
    }

    /**
     * 设置用户 ID 列表.
     *
     * @param userIds 用户 ID 列表
     */
    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}
