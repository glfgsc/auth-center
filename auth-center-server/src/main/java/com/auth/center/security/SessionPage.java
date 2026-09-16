package com.auth.center.security;

import java.util.List;

/**
 * 活跃会话的一页 —— 本页记录 + 命中总数.
 *
 * {@code total} 是按同一组过滤条件命中的总数,不是全表会话数;前端据它渲染分页器。
 *
 * @param records 本页会话(按最近活跃倒序)
 * @param total 该过滤条件下的命中总数
 */
public record SessionPage(List<SessionInfo> records, long total) {

    /** 空页。 */
    public static final SessionPage EMPTY = new SessionPage(List.of(), 0L);
}
