package edu.cga.integration;

import java.time.LocalDate;

import edu.cga.common.ApiResponse;
import edu.cga.integration.research.ResearchProjectService;
import edu.cga.integration.sis.SisSyncService;
import edu.cga.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部系统对接接口（P2 B.2、B.3，均为模拟实现）：
 * <ul>
 *   <li>POST /api/integration/sis/sync：学籍同步（校级管理员、国际生办），返回差异清单；</li>
 *   <li>GET /api/integration/research/projects/{code}?deptId=&amp;deadline=&amp;weeks=：查询科研项目并按建岗规则预校验
 *       （导师、院系管理员、校级管理员）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/integration")
public class IntegrationController {

    private final SisSyncService sisSync;
    private final ResearchProjectService research;

    public IntegrationController(SisSyncService sisSync, ResearchProjectService research) {
        this.sisSync = sisSync;
        this.research = research;
    }

    @PostMapping("/sis/sync")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'INTL_OFFICER')")
    public ApiResponse<SisSyncService.SisSyncResult> syncSis(@AuthenticationPrincipal CurrentUser user) {
        SisSyncService.SisSyncResult r = sisSync.sync(user);
        String msg = r.diffs().isEmpty()
                ? "学籍同步完成（" + r.source() + "），本次无差异"
                : "学籍同步完成（" + r.source() + "），更新 " + r.changedStudents() + " 名学生、" + r.diffs().size() + " 项字段";
        return ApiResponse.ok(r, msg);
    }

    @GetMapping("/research/projects/{code}")
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<ResearchProjectService.ProjectCheck> researchProject(
            @PathVariable String code,
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
            @RequestParam(required = false) Integer weeks,
            @AuthenticationPrincipal CurrentUser user) {
        ResearchProjectService.ProjectCheck r = research.lookup(code, deptId, user, deadline, weeks);
        return ApiResponse.ok(r, r.ok() ? "项目校验通过" : r.firstError());
    }
}
