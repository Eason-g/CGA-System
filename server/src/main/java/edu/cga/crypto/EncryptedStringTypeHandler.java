package edu.cga.crypto;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 加密字符串列（P2 D.1）：写入时 AES-256-GCM 加密，读出时解密；读到不带 enc:v1: 前缀的历史明文原样返回。
 * <p>
 * 用法：实体上 {@code @TableName(autoResultMap = true)}，字段上
 * {@code @TableField(typeHandler = EncryptedStringTypeHandler.class)}。
 * 注意：QueryWrapper 的 eq/like 条件不会经过 TypeHandler，也无法对随机 IV 的密文做等值匹配，
 * 需要按这类字段检索时应另存 HMAC 哈希列（如 users.email_hash）。
 * <p>
 * 写入时一律加密、不判断“是否已经是密文”：否则用户把邮箱填成 enc:v1: 开头的字符串就会原样落库，
 * 之后每次读取都解密失败。
 * <p>
 * 刻意不加 {@code @MappedTypes(String.class)}：只在字段上显式指定，避免被扫描注册成全局 String 处理器。
 */
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, FieldCrypto.get().encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return FieldCrypto.get().decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return FieldCrypto.get().decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return FieldCrypto.get().decrypt(cs.getString(columnIndex));
    }
}
