package com.auth.center.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充配置.
 *
 * <p>统一处理实体中 {@code createTime / createdAt / updateTime / updatedAt} 字段的自动填充，
 * 避免业务代码手动设置时间戳。</p>
 */
@Configuration
public class MyBatisPlusConfig implements MetaObjectHandler {

    private static final Logger log = LoggerFactory.getLogger(MyBatisPlusConfig.class);

    /** createTime 字段名 */
    private static final String FIELD_CREATE_TIME = "createTime";

    /** createdAt 字段名 */
    private static final String FIELD_CREATED_AT = "createdAt";

    /** updateTime 字段名 */
    private static final String FIELD_UPDATE_TIME = "updateTime";

    /** updatedAt 字段名 */
    private static final String FIELD_UPDATED_AT = "updatedAt";

    /**
     * 插入时自动填充.
     *
     * <p>填充 createTime / createdAt 和 updateTime / updatedAt 为当前时间。</p>
     *
     * @param metaObject 元对象，封装了待插入的实体
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("MyBatis-Plus 插入自动填充触发");
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, FIELD_CREATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_CREATED_AT, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_UPDATED_AT, LocalDateTime.class, now);
    }

    /**
     * 更新时自动填充.
     *
     * <p>填充 updateTime / updatedAt 为当前时间。</p>
     *
     * @param metaObject 元对象，封装了待更新的实体
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("MyBatis-Plus 更新自动填充触发");
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, FIELD_UPDATED_AT, LocalDateTime.class, now);
    }
}
