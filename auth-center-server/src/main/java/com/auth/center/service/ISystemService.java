package com.auth.center.service;

import com.auth.center.entity.AuthSystem;
import java.util.List;

/**
 * 系统注册表服务 -- 提供已接入平台/系统清单的查询.
 *
 * 管理台「用户与权限」页据此动态渲染系统分组，替代前端硬编码系统白名单。
 */
public interface ISystemService {

    /**
     * 列出全部已接入系统（按展示顺序升序）.
     *
     * @return 系统列表
     */
    List<AuthSystem> list();
}
