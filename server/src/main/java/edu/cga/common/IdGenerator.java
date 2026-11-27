package edu.cga.common;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 字符串主键生成器，格式：前缀_6 位大写字母数字，例如 JOB_7F3K2A。
 * 种子数据保留原 ID，新建记录才用它。
 */
@Component
public class IdGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String next(String prefix) {
        char[] buf = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return prefix + "_" + new String(buf);
    }
}
