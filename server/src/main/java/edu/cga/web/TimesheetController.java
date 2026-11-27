package edu.cga.web;

import java.math.BigDecimal;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.Timesheet;
import edu.cga.security.CurrentUser;
import edu.cga.service.TimesheetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 周工时：对应 api.js 的 submitTimesheet、reviewTimesheet。
 */
@RestController
@RequestMapping("/api/timesheets")
public class TimesheetController {

    private final TimesheetService timesheetService;

    public TimesheetController(TimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    public record SubmitRequest(
            @NotBlank(message = "请选择已生效的聘用协议") String contractId,
            @NotNull(message = "请填写周次") @Min(value = 1, message = "周次必须为正整数") Integer weekNo,
            @NotNull(message = "请填写工时") @Positive(message = "填报工时必须大于 0 小时")
            @Digits(integer = 3, fraction = 1, message = "工时最多保留一位小数") BigDecimal hours,
            @NotBlank(message = "请填写工作内容记录") @Size(max = 2000, message = "工作记录不能超过 2000 字") String workLog,
            @Size(max = 255, message = "关联里程碑过长") String milestoneRef) {
    }

    public record ReviewRequest(
            @NotBlank(message = "请选择审核结论")
            @Pattern(regexp = "APPROVE|REJECT", message = "decision 取值必须为 APPROVE 或 REJECT") String decision,
            String comment) {
    }

    /** POST /api/timesheets：学生本人，合同须 ACTIVE */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Timesheet> submit(@Valid @RequestBody SubmitRequest req,
                                         @AuthenticationPrincipal CurrentUser user) {
        Timesheet ts = timesheetService.submit(req.contractId(), req.weekNo(), req.hours(), req.workLog(),
                req.milestoneRef(), user);
        return ApiResponse.ok(ts, "工时已提交，等待导师审核");
    }

    /** POST /api/timesheets/{id}/review：岗位导师本人、本院系管理员、校级管理员 */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<Timesheet> review(@PathVariable String id, @Valid @RequestBody ReviewRequest req,
                                         @AuthenticationPrincipal CurrentUser user) {
        Timesheet ts = timesheetService.review(id, req.decision(), req.comment(), user);
        return ApiResponse.ok(ts, "APPROVE".equals(req.decision()) ? "已审核通过，工时将纳入本月薪酬核算" : "已驳回该工时单");
    }
}
