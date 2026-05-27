package com.auth.center.service.impl;

import com.auth.center.entity.AuthGroup;
import com.auth.center.entity.AuthGroupMember;
import com.auth.center.mapper.AuthGroupMapper;
import com.auth.center.mapper.AuthGroupMemberMapper;
import com.auth.center.service.IGroupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户组服务实现 -- CRUD + 成员管理 + all_users 自动维护.
 */
@Service
public class GroupServiceImpl implements IGroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupServiceImpl.class);

    /** all_users 系统组编码 */
    private static final String ALL_USERS_CODE = "all_users";

    private final AuthGroupMapper groupMapper;
    private final AuthGroupMemberMapper memberMapper;

    /**
     * 构造函数.
     *
     * @param groupMapper  用户组 Mapper
     * @param memberMapper 组成员 Mapper
     */
    public GroupServiceImpl(AuthGroupMapper groupMapper, AuthGroupMemberMapper memberMapper) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public List<AuthGroup> listAll() {
        return groupMapper.selectList(
                new LambdaQueryWrapper<AuthGroup>().orderByAsc(AuthGroup::getId));
    }

    @Override
    public AuthGroup getById(Long id) {
        return groupMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthGroup create(AuthGroup group, Long creatorId) {
        group.setCreatedBy(creatorId);
        group.setIsSystem(0);
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.insert(group);
        log.info("[Group] created: id={}, code={}", group.getId(), group.getCode());
        return group;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthGroup update(Long id, AuthGroup patch) {
        AuthGroup existing = groupMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("group not found: " + id);
        }
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        existing.setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AuthGroup group = groupMapper.selectById(id);
        if (group == null) return;
        if (group.getIsSystem() != null && group.getIsSystem() == 1) {
            throw new IllegalStateException("cannot delete system group: " + group.getCode());
        }
        memberMapper.delete(
                new LambdaQueryWrapper<AuthGroupMember>().eq(AuthGroupMember::getGroupId, id));
        groupMapper.deleteById(id);
        log.info("[Group] deleted: id={}, code={}", id, group.getCode());
    }

    @Override
    public List<AuthGroupMember> listMembers(Long groupId) {
        return memberMapper.selectMembersWithUserInfo(groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMembers(Long groupId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        for (Long userId : userIds) {
            Long count = memberMapper.selectCount(
                    new LambdaQueryWrapper<AuthGroupMember>()
                            .eq(AuthGroupMember::getGroupId, groupId)
                            .eq(AuthGroupMember::getUserId, userId));
            if (count > 0) continue;

            AuthGroupMember gm = new AuthGroupMember();
            gm.setGroupId(groupId);
            gm.setUserId(userId);
            gm.setCreatedAt(LocalDateTime.now());
            memberMapper.insert(gm);
        }
        log.info("[Group] addMembers: groupId={}, userIds={}", groupId, userIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMembers(Long groupId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        memberMapper.delete(
                new LambdaQueryWrapper<AuthGroupMember>()
                        .eq(AuthGroupMember::getGroupId, groupId)
                        .in(AuthGroupMember::getUserId, userIds));
        log.info("[Group] removeMembers: groupId={}, userIds={}", groupId, userIds);
    }

    @Override
    public List<Long> getGroupIdsByUserId(Long userId) {
        return memberMapper.selectGroupIdsByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureAllUsersGroup(Long userId) {
        AuthGroup allUsers = groupMapper.selectByCode(ALL_USERS_CODE);
        if (allUsers == null) {
            log.warn("[Group] all_users group not found, skip auto-join for userId={}", userId);
            return;
        }
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<AuthGroupMember>()
                        .eq(AuthGroupMember::getGroupId, allUsers.getId())
                        .eq(AuthGroupMember::getUserId, userId));
        if (count > 0) return;

        AuthGroupMember gm = new AuthGroupMember();
        gm.setGroupId(allUsers.getId());
        gm.setUserId(userId);
        gm.setCreatedAt(LocalDateTime.now());
        memberMapper.insert(gm);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAllMembershipsForUser(Long userId) {
        memberMapper.delete(
                new LambdaQueryWrapper<AuthGroupMember>()
                        .eq(AuthGroupMember::getUserId, userId));
        log.info("[Group] removed all memberships for userId={}", userId);
    }
}
