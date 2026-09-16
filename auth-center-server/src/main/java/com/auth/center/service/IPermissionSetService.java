package com.auth.center.service;

import com.auth.center.entity.PermissionSet;
import java.util.List;

/**
 * 权限集服务接口 -- 提供权限集 CRUD 及用户权限解析操作.
 *
 * 权限集是系统权限模型的核心概念，每个权限集包含一组能力码（capabilities），用户通过关联权限集获得对应的操作权限。
 */
public interface IPermissionSetService {

    /**
     * 查询所有权限集列表.
     *
     * @return 权限集列表
     */
    List<PermissionSet> list();

    /**
     * 查询「指定系统」可用的权限集列表（该系统 + global）.
     *
     * @param systemCode 系统编码（如 bi / tracking）；为空时返回全部
     * @return 权限集列表
     */
    List<PermissionSet> list(String systemCode);

    /**
     * 根据 ID 查询权限集.
     *
     * @param id 权限集主键 ID
     * @return 权限集实体，不存在时返回 {@code null}
     */
    PermissionSet getById(Long id);

    /**
     * 根据编码查询权限集.
     *
     * @param code 权限集编码，如 "admin"、"viewer"
     * @return 权限集实体，不存在时返回 {@code null}
     */
    PermissionSet getByCode(String code);

    /**
     * 保存权限集（新增或更新）.
     *
     * 若 id 为 {@code null} 则新增，否则更新已有记录。
     *
     * @param ps 权限集实体
     * @return 保存后的权限集主键 ID
     */
    Long save(PermissionSet ps);

    /**
     * 删除权限集.
     *
     * 系统预设权限集（isSystem=1）不允许删除。
     *
     * @param id 权限集主键 ID
     * @throws IllegalStateException 尝试删除系统预设权限集时抛出
     */
    void delete(Long id);

    /**
     * 解析指定用户的权限集.
     *
     * 查询用户关联的权限集，若未关联则降级到默认 viewer 权限集。
     *
     * @param userId 用户 ID
     * @return 该用户的权限集，保证非 {@code null}（至少返回空 viewer）
     */
    PermissionSet resolveForUser(Long userId);
}
