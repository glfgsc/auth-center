package com.auth.center.service;

import com.auth.center.entity.SystemCapability;
import java.util.List;

/**
 * 系统能力码服务接口 -- 各子系统向认证中心注册 / 查询能力码.
 *
 * 能力码是权限模型的最小粒度单元；注册为「按系统全量替换」语义（先删该系统旧能力码，再插新的），数据访问收口到 service 层，Controller 不直连 Mapper。
 */
public interface ISystemCapabilityService {

    /**
     * 全量替换某子系统的能力码：先删除该系统编码下的旧能力码，再批量插入新的（单事务）.
     *
     * @param systemCode 子系统编码（如 bi / flow）
     * @param capabilities 待写入的能力码（{@code systemCode} 由本方法统一回填，调用方无需设置）
     * @return 实际写入的能力码条数
     */
    int replaceForSystem(String systemCode, List<SystemCapability> capabilities);

    /**
     * 查询已注册的能力码列表.
     *
     * @param systemCode 子系统编码；为空 / 空白时返回全部
     * @return 按 systemCode、category 升序排列的能力码列表
     */
    List<SystemCapability> list(String systemCode);
}
