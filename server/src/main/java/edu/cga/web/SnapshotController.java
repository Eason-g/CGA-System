package edu.cga.web;

import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.security.CurrentUser;
import edu.cga.service.SnapshotService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/snapshot：对应 api.js 的 fetchSnapshot，返回按数据范围裁剪后的 db 对象。
 */
@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final SnapshotService snapshotService;

    public SnapshotController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/snapshot")
    public ApiResponse<Map<String, Object>> snapshot(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(snapshotService.snapshot(user));
    }
}
