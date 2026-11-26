package edu.cga.integration.sso;

/**
 * 统一身份认证返回的身份。
 *
 * @param uid  学工号（学生为学号，教职工为工号/用户名），用于映射本系统用户
 * @param name 认证中心登记的姓名（仅用于日志与提示）
 */
public record SsoIdentity(String uid, String name) {
}
