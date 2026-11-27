package edu.cga.common;

import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 按请求头 Accept-Language 判断是否返回英文提示：优先级最高的语言是 en（en、en-US、en-GB 等）时为英文，
 * 不带头、zh-CN 或无法解析时都按中文处理（响应与原来完全一致）。
 */
public final class Lang {

    private Lang() {
    }

    public static boolean isEnglish(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return false;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
            if (ranges.isEmpty()) {
                return false;
            }
            String first = ranges.get(0).getRange().toLowerCase(Locale.ROOT);
            return first.equals("en") || first.startsWith("en-");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isEnglish(HttpServletRequest request) {
        return request != null && isEnglish(request.getHeader("Accept-Language"));
    }
}
