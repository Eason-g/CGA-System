package edu.cga.web;

import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.common.BizException;
import edu.cga.config.AppProperties;
import edu.cga.security.CurrentUser;
import edu.cga.service.DatabaseInitializer;
import edu.cga.service.SnapshotService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/demo/reset：对应 api.js 的 resetDemoData。
 * 仅校级管理员，且需 app.demo.reset-enabled=true；等同于执行一次 schema.sql + data.sql 重建。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DatabaseInitializer initializer;
    private final SnapshotService snapshotService;
    private final AppProperties props;

    public DemoController(DatabaseInitializer initializer, SnapshotService snapshotService, AppProperties props) {
        this.initializer = initializer;
        this.snapshotService = snapshotService;
        this.props = props;
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ApiResponse<Map<String, Object>> reset(@AuthenticationPrincipal CurrentUser user) {
        if (!props.getDemo().isResetEnabled()) {
            throw BizException.forbidden("演示数据重置功能未开启");
        }
        initializer.rebuild();
        return ApiResponse.ok(snapshotService.snapshot(user), "已重置为初始演示数据");
    }
}
