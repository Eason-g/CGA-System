package edu.cga.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 一张待导出的二维表：表头 + 数据行。单元格可以是 String、Number（含 BigDecimal）或 null。
 */
public final class ReportTable {

    private final String title;
    private final List<String> headers;
    private final List<List<Object>> rows = new ArrayList<>();

    public ReportTable(String title, String... headers) {
        this.title = title;
        this.headers = List.of(headers);
    }

    public ReportTable row(Object... cells) {
        if (cells.length != headers.size()) {
            throw new IllegalArgumentException("列数与表头不一致：" + title);
        }
        rows.add(Arrays.asList(cells));
        return this;
    }

    public String title() {
        return title;
    }

    public List<String> headers() {
        return headers;
    }

    public List<List<Object>> rows() {
        return rows;
    }
}
