package edu.cga.common;

/**
 * 一条同时带中文与英文的提示（docs/双语设计.md 第 3 节）：zh 是原有的中文文案，en 是给国际生看的英文。
 */
public record BiText(String zh, String en) {

    public static BiText of(String zh, String en) {
        return new BiText(zh, en);
    }

    /** 前缀拼接：prefixZh + zh、prefixEn + en */
    public BiText prepend(String prefixZh, String prefixEn) {
        return new BiText(prefixZh + zh, prefixEn + en);
    }
}
