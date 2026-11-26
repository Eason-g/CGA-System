package edu.cga.scope;

/**
 * 敏感字段打码：手机号、邮箱只对本人和校级管理员明文。
 */
public final class Masker {

    private Masker() {
    }

    /** 138-0010-2091 → 138****2091 */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "****";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    /** alex.chen@univ.edu.cn → a***@univ.edu.cn */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
