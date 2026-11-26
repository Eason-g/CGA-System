package edu.cga.web;

import java.nio.charset.StandardCharsets;

import edu.cga.report.ReportExportService;
import edu.cga.security.CurrentUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表导出（P1 第二批 2.1）。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportExportService exportService;

    public ReportController(ReportExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * GET /api/reports/{type}/export?format=xlsx|csv&month=&deptId=
     * <p>
     * type：jobs 岗位统计 / hires 聘用名单 / timesheets 工时汇总 / payrolls 薪酬发放 / budgets 经费执行。
     * 管理端角色可用（学生 403），内容按 DataScope 裁剪；文件名用 filename* 编码中文。
     * 出错时返回普通的 { code, data, message } JSON。
     */
    @GetMapping("/{type}/export")
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'INTL_OFFICER', 'SCHOOL_ADMIN')")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(required = false) String month,
                                         @RequestParam(required = false) String deptId,
                                         @AuthenticationPrincipal CurrentUser user) {
        ReportExportService.ExportFile file = exportService.export(type, format, month, deptId, user);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Report-Rows", String.valueOf(file.rowCount()))
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
