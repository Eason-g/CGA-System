package edu.cga.scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 一段参数化的 WHERE 条件，SQL 中用 ? 作占位符，参数按顺序放在 args 中。
 * <p>
 * 三种形态：
 * <ul>
 *   <li>{@link #ALL}：不加限制（校级管理员）；</li>
 *   <li>{@link #NONE}：一条都不可见（生成 1 = 0）；</li>
 *   <li>普通条件：如 {@code dept_id = ?}。</li>
 * </ul>
 * 应用到 MyBatis-Plus 查询时会把 ? 依次换成 {0}、{1}…，仍然走预编译参数，不拼接用户输入。
 */
public final class SqlCondition {

    public static final SqlCondition ALL = new SqlCondition(null, List.of());
    public static final SqlCondition NONE = new SqlCondition("1 = 0", List.of());

    private final String sql;
    private final List<Object> args;

    private SqlCondition(String sql, List<Object> args) {
        this.sql = sql;
        this.args = Collections.unmodifiableList(args);
    }

    public static SqlCondition of(String sql, Object... args) {
        List<Object> list = new ArrayList<>(args.length);
        Collections.addAll(list, args);
        long placeholders = sql.chars().filter(c -> c == '?').count();
        if (placeholders != list.size()) {
            throw new IllegalArgumentException("占位符数量与参数数量不一致：" + sql);
        }
        return new SqlCondition(sql, list);
    }

    public boolean isAll() {
        return sql == null;
    }

    public boolean isNone() {
        return this == NONE;
    }

    public String sql() {
        return sql;
    }

    public List<Object> args() {
        return args;
    }

    /** 用 OR 合并多个条件；任一为 ALL 则结果为 ALL，NONE 会被忽略 */
    public static SqlCondition anyOf(SqlCondition... parts) {
        StringBuilder sb = new StringBuilder();
        List<Object> all = new ArrayList<>();
        for (SqlCondition p : parts) {
            if (p.isAll()) {
                return ALL;
            }
            if (p.isNone()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" OR ");
            }
            sb.append('(').append(p.sql).append(')');
            all.addAll(p.args);
        }
        return sb.isEmpty() ? NONE : new SqlCondition(sb.toString(), all);
    }

    /**
     * 生成 “column IN (SELECT selectCol FROM table WHERE 本条件)”。
     * 本条件为 ALL 时子查询不带 WHERE；为 NONE 时结果为 NONE。
     */
    public SqlCondition asInSubquery(String column, String selectCol, String table) {
        if (isNone()) {
            return NONE;
        }
        String sub = "SELECT " + selectCol + " FROM " + table + (isAll() ? "" : " WHERE " + sql);
        return new SqlCondition(column + " IN (" + sub + ")", new ArrayList<>(args));
    }

    /** 在本条件前加一个 AND 前置条件，例如 target_type = 'JOB' AND (...) */
    public SqlCondition and(String prefixSql) {
        if (isNone()) {
            return NONE;
        }
        if (isAll()) {
            return new SqlCondition(prefixSql, List.of());
        }
        return new SqlCondition(prefixSql + " AND (" + sql + ")", new ArrayList<>(args));
    }

    /** 把条件加到 MyBatis-Plus 查询上 */
    public <T> QueryWrapper<T> applyTo(QueryWrapper<T> wrapper) {
        if (isAll()) {
            return wrapper;
        }
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (char c : sql.toCharArray()) {
            if (c == '?') {
                sb.append('{').append(idx++).append('}');
            } else {
                sb.append(c);
            }
        }
        return wrapper.apply("(" + sb + ")", args.toArray());
    }

    @Override
    public String toString() {
        return isAll() ? "<ALL>" : sql + " " + args;
    }
}
