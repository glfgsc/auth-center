package com.auth.center.security.crypto;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis TypeHandler: 写入时 AES-GCM 加密,读取时自动解密.
 *
 * 用法:实体字段标 {@code @TableField(typeHandler = EncryptedStringTypeHandler.class)}, 并且实体类上要有
 * {@code autoResultMap = true} -- 少了它 SELECT 侧不会应用本 Handler, 表现成 "写进去是密文、读出来也是密文", 而写入路径看着完全正常.
 *
 * {@link CryptoService} 由 {@link CryptoServiceTypeHandlerBridge} 在启动阶段静态注入;注入前以 passthrough
 * 降级并打一次 WARN.
 *
 * 解密失败(密钥轮换、密文损坏、遗留明文行)时回退返回原值,不阻塞读取与后续迁移.
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringTypeHandler.class);

    /** 由 {@link CryptoServiceTypeHandlerBridge} 注入的加解密服务. */
    private static volatile ICryptoService crypto;

    /** "尚未桥接"只打一次. */
    private static volatile boolean missingLogged;

    /**
     * 注册 Spring 管理的加解密服务.
     *
     * @param service 加解密服务,由桥接 Bean 在启动时调用
     */
    static void install(ICryptoService service) {
        crypto = service;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String value, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, encryptSafe(value));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decryptSafe(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decryptSafe(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decryptSafe(cs.getString(columnIndex));
    }

    private static String encryptSafe(String value) {
        if (value == null || value.isEmpty()) return value;
        ICryptoService c = crypto;
        if (c == null) {
            warnMissing();
            return value;
        }
        return c.encrypt(value);
    }

    private static String decryptSafe(String value) {
        if (value == null || value.isEmpty()) return value;
        ICryptoService c = crypto;
        if (c == null) {
            warnMissing();
            return value;
        }
        if (!c.isEncrypted(value)) {
            return value;
        }
        try {
            return c.decrypt(value);
        } catch (Exception e) {
            log.warn(
                    "[EncryptedStringTypeHandler] decrypt failed (len={}); returning raw value."
                            + " Likely cause: master key rotated or column corruption.",
                    value.length(),
                    e);
            return value;
        }
    }

    private static void warnMissing() {
        if (!missingLogged) {
            log.warn(
                    "[EncryptedStringTypeHandler] CryptoService not wired yet; passing values"
                            + " through unencrypted. Normal during Spring init, must not persist into"
                            + " request-serving time.");
            missingLogged = true;
        }
    }
}
