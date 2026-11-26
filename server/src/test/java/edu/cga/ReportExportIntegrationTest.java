package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.report.ReportWriter;
import edu.cga.service.AuditChainService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 报表导出（P1 第二批 2.1）：5 种报表 × xlsx/csv、BOM、公式注入转义、数据范围、权限、审计日志。
 */
class ReportExportIntegrationTest extends WriteApiTestBase {

    private static final String ADMIN = "zhou.guoqiang";
    private static final String MENTOR = "zhang.mingyuan";
    private static final String MENTOR_MATH = "chen.shouren";

    /** 各报表的首列表头 */
    private static final Map<String, String> FIRST_HEADER = Map.of(
            "jobs", "岗位编号", "hires", "协议编号", "timesheets", "学生", "payrolls", "月份", "budgets", "经费编号");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditChainService auditChain;

    /** 数学学院导师名下一份合同，用来验证计算机学院导师导出时看不到它 */
    @BeforeAll
    void prepareOtherMentorData() {
        createStudent("T_STU_MATH", "DEPT_MATH", false, true);
        Job job = createPublishedJob("T_JOB_MATH", "U_MENTOR_MATH", "DEPT_MATH", "B_MATH_TA", 6, false);
        Contract c = createActiveContract("T_CON_MATH", "T_STU_MATH", job);
        createApprovedTimesheet("T_TS_MATH", c, 11, "2026-11", "6");
        createPayroll("T_PAY_MATH", c, job, "2026-11", "240", "PENDING_PAY");
    }

    private MockHttpServletResponse download(String account, String url) throws Exception {
        var req = get(url);
        if (account != null) {
            req.header("Authorization", bearer(tokenOf(account)));
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** 校级管理员（全部数据）下各报表应有的行数 */
    private long expectedAdminRows(String type) {
        return switch (type) {
            case "jobs" -> count("SELECT COUNT(*) FROM jobs");
            case "hires" -> count("SELECT COUNT(*) FROM contracts");
            case "timesheets" -> count("SELECT COUNT(*) FROM (SELECT 1 FROM timesheets GROUP BY student_id, month) t");
            case "payrolls" -> count("SELECT COUNT(*) FROM payrolls");
            case "budgets" -> count("SELECT COUNT(*) FROM budgets");
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static Sheet readSheet(byte[] bytes) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes));
        return wb.getSheetAt(0);
    }

    private static List<String> csvLines(byte[] bytes) {
        assertThat(Arrays.copyOf(bytes, 3)).as("csv 应以 UTF-8 BOM 开头").isEqualTo(ReportWriter.BOM);
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r\n")));
        lines.removeIf(String::isEmpty);
        return lines;
    }

    // ---------------------------------------------------------------- 每种报表都能生成

    @ParameterizedTest
    @ValueSource(strings = {"jobs", "hires", "timesheets", "payrolls", "budgets"})
    void everyReportExportsAsXlsx(String type) throws Exception {
        MockHttpServletResponse resp = download(ADMIN, "/api/reports/" + type + "/export?format=xlsx");
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getContentType()).startsWith(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(resp.getHeader("Content-Disposition")).contains("filename*=UTF-8''");

        Sheet sheet = readSheet(resp.getContentAsByteArray());
        Row header = sheet.getRow(0);
        assertThat(header.getCell(0).getStringCellValue()).isEqualTo(FIRST_HEADER.get(type));
        assertThat(sheet.getWorkbook().getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold())
                .as("表头加粗").isTrue();
        assertThat(sheet.getPaneInformation()).as("冻结首行").isNotNull();
        assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
        long expected = expectedAdminRows(type);
        assertThat(expected).isPositive();
        assertThat(sheet.getLastRowNum()).isEqualTo((int) expected);
        assertThat(resp.getHeader("X-Report-Rows")).isEqualTo(String.valueOf(expected));
        sheet.getWorkbook().close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jobs", "hires", "timesheets", "payrolls", "budgets"})
    void everyReportExportsAsCsvWithBom(String type) throws Exception {
        MockHttpServletResponse resp = download(ADMIN, "/api/reports/" + type + "/export?format=csv");
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getContentType()).startsWith("text/csv");
        List<String> lines = csvLines(resp.getContentAsByteArray());
        assertThat(lines.get(0)).startsWith(FIRST_HEADER.get(type) + ",");
        assertThat(lines).hasSize((int) expectedAdminRows(type) + 1);
    }

    @Test
    void fileNameUsesMonthAndEncodesChinese() throws Exception {
        MockHttpServletResponse resp = download(ADMIN, "/api/reports/hires/export?format=xlsx&month=2026-11");
        assertThat(resp.getStatus()).isEqualTo(200);
        String expected = java.net.URLEncoder.encode("聘用名单_2026-11.xlsx", StandardCharsets.UTF_8);
        assertThat(resp.getHeader("Content-Disposition")).startsWith("attachment").contains(expected);
    }

    @Test
    void numericColumnsAreNumbers() throws Exception {
        Sheet sheet = readSheet(download(ADMIN, "/api/reports/budgets/export?format=xlsx").getContentAsByteArray());
        Row first = sheet.getRow(1);
        assertThat(first.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
        sheet.getWorkbook().close();
    }

    // ---------------------------------------------------------------- 公式注入

    @Test
    void formulaLikeTextIsEscaped() throws Exception {
        Job job = createPublishedJob("T_JOB_FORMULA", "U_MENTOR", "DEPT_CS", "B_CS_RA", 4, false);
        job.setTitle("=HYPERLINK(\"http://evil.example\",\"点我\")");
        jobMapper.updateById(job);
        Job job2 = createPublishedJob("T_JOB_FORMULA2", "U_MENTOR", "DEPT_CS", "B_CS_RA", 4, false);
        job2.setTitle("@SUM(1+1)");
        jobMapper.updateById(job2);
        Job job3 = createPublishedJob("T_JOB_FORMULA3", "U_MENTOR", "DEPT_CS", "B_CS_RA", 4, false);
        job3.setTitle("+1-1");
        jobMapper.updateById(job3);

        Sheet sheet = readSheet(download(ADMIN, "/api/reports/jobs/export?format=xlsx").getContentAsByteArray());
        List<String> titles = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Cell c = sheet.getRow(i).getCell(1);
            assertThat(c.getCellType()).isEqualTo(CellType.STRING);
            titles.add(c.getStringCellValue());
        }
        assertThat(titles).contains("'=HYPERLINK(\"http://evil.example\",\"点我\")", "'@SUM(1+1)", "'+1-1");
        assertThat(titles).noneMatch(t -> t.startsWith("=") || t.startsWith("@") || t.startsWith("+"));
        sheet.getWorkbook().close();

        String csv = String.join("\n", csvLines(
                download(ADMIN, "/api/reports/jobs/export?format=csv").getContentAsByteArray()));
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"点我\"\")\"", ",'@SUM(1+1),", ",'+1-1,");

        jobMapper.deleteById("T_JOB_FORMULA");
        jobMapper.deleteById("T_JOB_FORMULA2");
        jobMapper.deleteById("T_JOB_FORMULA3");
    }

    @Test
    void escapeFormulaRules() {
        assertThat(ReportWriter.escapeFormula("=1+1")).isEqualTo("'=1+1");
        assertThat(ReportWriter.escapeFormula("-2")).isEqualTo("'-2");
        assertThat(ReportWriter.escapeFormula("\tx")).isEqualTo("'\tx");
        assertThat(ReportWriter.escapeFormula("\rx")).isEqualTo("'\rx");
        assertThat(ReportWriter.escapeFormula("正常文本")).isEqualTo("正常文本");
        assertThat(ReportWriter.escapeFormula("")).isEmpty();
    }

    // ---------------------------------------------------------------- 数据范围

    @Test
    void mentorExportOnlyContainsOwnScope() throws Exception {
        // 聘用名单：只含本人岗位下的合同
        List<String> hires = csvLines(download(MENTOR, "/api/reports/hires/export?format=csv").getContentAsByteArray());
        long own = count("SELECT COUNT(*) FROM contracts WHERE job_id IN (SELECT id FROM jobs WHERE mentor_id = 'U_MENTOR')");
        assertThat(own).isPositive();
        assertThat(hires).hasSize((int) own + 1);
        assertThat(hires).noneMatch(l -> l.contains("T_CON_MATH"));

        // 薪酬、工时同理
        List<String> pays = csvLines(download(MENTOR, "/api/reports/payrolls/export?format=csv").getContentAsByteArray());
        long ownPays = count("SELECT COUNT(*) FROM payrolls WHERE job_id IN (SELECT id FROM jobs WHERE mentor_id = 'U_MENTOR')");
        assertThat(pays).hasSize((int) ownPays + 1);
        assertThat(pays).noneMatch(l -> l.contains("测试学生T_STU_MATH"));
        List<String> ts = csvLines(download(MENTOR, "/api/reports/timesheets/export?format=csv").getContentAsByteArray());
        assertThat(ts).noneMatch(l -> l.contains("测试学生T_STU_MATH"));

        // 岗位：本院系 + 本人发布，看不到数学学院岗位
        List<String> jobs = csvLines(download(MENTOR, "/api/reports/jobs/export?format=csv").getContentAsByteArray());
        long ownJobs = count("SELECT COUNT(*) FROM jobs WHERE dept_id = 'DEPT_CS' OR mentor_id = 'U_MENTOR'");
        assertThat(jobs).hasSize((int) ownJobs + 1);
        assertThat(jobs).noneMatch(l -> l.startsWith("T_JOB_MATH,"));

        // 经费：本院系 + GA_FUND
        List<String> budgets = csvLines(download(MENTOR, "/api/reports/budgets/export?format=csv").getContentAsByteArray());
        long ownBudgets = count("SELECT COUNT(*) FROM budgets WHERE dept_id = 'DEPT_CS' OR source_type = 'GA_FUND'");
        assertThat(budgets).hasSize((int) ownBudgets + 1);

        // 指定外院 deptId 也拿不到数据
        List<String> other = csvLines(download(MENTOR,
                "/api/reports/hires/export?format=csv&deptId=DEPT_MATH").getContentAsByteArray());
        assertThat(other).hasSize(1);

        // 数学学院导师能导出自己的那份合同
        List<String> math = csvLines(download(MENTOR_MATH, "/api/reports/hires/export?format=csv").getContentAsByteArray());
        assertThat(math).anyMatch(l -> l.startsWith("T_CON_MATH,"));
        assertThat(math).hasSize((int) count(
                "SELECT COUNT(*) FROM contracts WHERE job_id IN (SELECT id FROM jobs WHERE mentor_id = 'U_MENTOR_MATH')") + 1);
    }

    @Test
    void monthFilterApplies() throws Exception {
        List<String> pays = csvLines(download(ADMIN,
                "/api/reports/payrolls/export?format=csv&month=2026-11").getContentAsByteArray());
        assertThat(pays).hasSize((int) count("SELECT COUNT(*) FROM payrolls WHERE month = '2026-11'") + 1);
        assertThat(pays.subList(1, pays.size())).allMatch(l -> l.startsWith("2026-11,"));
    }

    @Test
    void deptAdminExportLimitedToOwnDept() throws Exception {
        List<String> budgets = csvLines(download("li.wanqing",
                "/api/reports/budgets/export?format=csv").getContentAsByteArray());
        assertThat(budgets).hasSize((int) count("SELECT COUNT(*) FROM budgets WHERE dept_id = 'DEPT_CS'") + 1);
    }

    // ---------------------------------------------------------------- 权限、参数、审计

    @Test
    void studentCannotExport() throws Exception {
        send(HttpMethod.GET, "/api/reports/hires/export?format=xlsx", "alex.chen", null, 403);
        send(HttpMethod.GET, "/api/reports/payrolls/export?format=csv", "lin.zhiyuan", null, 403);
        send(HttpMethod.GET, "/api/reports/hires/export", null, null, 401);
    }

    @Test
    void badParametersReturn400() throws Exception {
        send(HttpMethod.GET, "/api/reports/unknown/export", ADMIN, null, 400);
        send(HttpMethod.GET, "/api/reports/jobs/export?format=pdf", ADMIN, null, 400);
        send(HttpMethod.GET, "/api/reports/payrolls/export?month=2026-13", ADMIN, null, 400);
    }

    @Test
    void exportWritesAuditLogOnChain() throws Exception {
        long before = count("SELECT COUNT(*) FROM audit_log WHERE target_type = 'REPORT'");
        assertThat(download(MENTOR, "/api/reports/hires/export?format=xlsx&month=2026-11").getStatus()).isEqualTo(200);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE target_type = 'REPORT'")).isEqualTo(before + 1);
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM audit_log WHERE target_type = 'REPORT' "
                + "ORDER BY seq DESC LIMIT 1");
        assertThat(log.get("operator_id")).isEqualTo("U_MENTOR");
        assertThat(log.get("target_id")).isEqualTo("hires");
        assertThat(log.get("after_value")).isEqualTo("聘用名单_2026-11.xlsx");
        assertThat((String) log.get("opinion")).contains("聘用名单").contains("2026-11");
        assertThat(log.get("seq")).isNotNull();
        assertThat(auditChain.verify().ok()).isTrue();

        // 导出人能在自己的快照里看到这条日志
        assertThat(snapshotAs(MENTOR).path("auditLogs").toString()).contains("聘用名单_2026-11.xlsx");
    }
}
