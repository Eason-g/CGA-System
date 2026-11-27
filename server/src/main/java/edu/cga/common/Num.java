package edu.cga.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * 数字工具：取值与格式化，保证后端文案与前端 JS 输出一致。
 * <ul>
 *   <li>{@link #plain}：等同 JS 模板字符串里的 `${n}`，如 12、12.5、1.1；</li>
 *   <li>{@link #locale}：等同 JS 的 n.toLocaleString()，千分位 + 最多 3 位小数，如 17,280；</li>
 *   <li>{@link #fixed2}：等同 JS 的 n.toFixed(2)。</li>
 * </ul>
 */
public final class Num {

    private Num() {
    }

    /** 把任意数值（Integer、Double、BigDecimal、数字字符串）转成 BigDecimal；无法解析返回 null */
    public static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            return BigDecimal.valueOf(((Number) v).longValue());
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? BigDecimal.valueOf(d) : null;
        }
        if (v instanceof Boolean bool) {
            return bool ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO; // 与 JS Number('') === 0 一致
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** null 视为 0 */
    public static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    /** 金额保留两位小数（四舍五入），等同 JS Math.round(x * 100) / 100 */
    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static String plain(Number v) {
        if (v == null) {
            return "0";
        }
        BigDecimal d = toDecimal(v);
        if (d == null) {
            return v.toString();
        }
        if (d.signum() == 0) {
            return "0";
        }
        return d.stripTrailingZeros().toPlainString();
    }

    public static String locale(Number v) {
        BigDecimal d = toDecimal(v);
        DecimalFormat f = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(d == null ? BigDecimal.ZERO : d);
    }

    public static String fixed2(Number v) {
        BigDecimal d = toDecimal(v);
        return (d == null ? BigDecimal.ZERO : d).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
