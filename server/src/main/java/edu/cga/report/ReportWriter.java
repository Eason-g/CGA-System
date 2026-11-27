package edu.cga.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 把 {@link ReportTable} 写成 xlsx 或 csv。
 * <ul>
 *   <li>xlsx：表头加粗、冻结首行、按内容估算列宽（中文按 2 个字符宽）；数字写成数值单元格；</li>
 *   <li>csv：UTF-8 带 BOM（Excel 直接双击打开不乱码），CRLF 换行，含逗号/引号/换行的字段加双引号；</li>
 *   <li>两种格式都防公式注入：以 = + - @ 或制表符、回车开头的<b>文本</b>单元格前加单引号。</li>
 * </ul>
 */
public final class ReportWriter {

    /** UTF-8 BOM */
    public static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final int MAX_COLUMN_CHARS = 60;

    private ReportWriter() {
    }

    /** 防公式注入：危险前缀的文本前加单引号，Excel/WPS 会把它当普通文本显示 */
    public static String escapeFormula(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + s;
        }
        return s;
    }

    // ------------------------------------------------------------------ xlsx

    public static byte[] xlsx(ReportTable table) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(table.title()));

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);
            DataFormat fmt = wb.createDataFormat();
            CellStyle decimalStyle = wb.createCellStyle();
            decimalStyle.setDataFormat(fmt.getFormat("#,##0.00"));

            List<String> headers = table.headers();
            int[] widths = new int[headers.size()];
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                widths[i] = displayWidth(headers.get(i));
            }

            int r = 1;
            for (List<Object> values : table.rows()) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < values.size(); i++) {
                    Object v = values.get(i);
                    if (v == null) {
                        continue;
                    }
                    Cell cell = row.createCell(i);
                    if (v instanceof BigDecimal bd) {
                        cell.setCellValue(bd.doubleValue());
                        if (bd.scale() > 0) {
                            cell.setCellStyle(decimalStyle);
                        }
                        widths[i] = Math.max(widths[i], displayWidth(bd.toPlainString()) + 2);
                    } else if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                        widths[i] = Math.max(widths[i], displayWidth(String.valueOf(n)));
                    } else {
                        String s = escapeFormula(String.valueOf(v));
                        cell.setCellValue(s);
                        widths[i] = Math.max(widths[i], displayWidth(s));
                    }
                }
            }

            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, (Math.min(widths[i], MAX_COLUMN_CHARS) + 2) * 256);
            }
            sheet.createFreezePane(0, 1);
            if (!headers.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, r - 1), 0, headers.size() - 1));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("生成 Excel 失败", e);
        }
    }

    /** 估算显示宽度：非 ASCII（中文等）按 2 个字符宽 */
    static int displayWidth(String s) {
        if (s == null) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) < 0x80 ? 1 : 2;
        }
        return w;
    }

    // ------------------------------------------------------------------ csv

    public static byte[] csv(ReportTable table) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, table.headers().stream().map(h -> (Object) h).toList());
        for (List<Object> row : table.rows()) {
            appendLine(sb, row);
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        return out;
    }

    private static void appendLine(StringBuilder sb, List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object v = cells.get(i);
            String s;
            if (v == null) {
                s = "";
            } else if (v instanceof BigDecimal bd) {
                s = bd.toPlainString();
            } else if (v instanceof Number n) {
                s = String.valueOf(n);
            } else {
                s = escapeFormula(String.valueOf(v));
            }
            if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
                s = '"' + s.replace("\"", "\"\"") + '"';
            }
            sb.append(s);
        }
        sb.append("\r\n");
    }
}
