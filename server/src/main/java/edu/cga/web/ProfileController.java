package edu.cga.web;

import java.util.List;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.User;
import edu.cga.security.CurrentUser;
import edu.cga.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人信息：对应 api.js 的 updateUserProfile、markNotificationsRead。只作用于当前登录用户本人。
 */
@RestController
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** 前端还会传 userId，后端忽略，一律以登录用户为准 */
    public record ProfileRequest(String email, String phone, List<String> skills, String bio) {
    }

    /** PUT /api/users/me：本人 */
    @PutMapping("/api/users/me")
    public ApiResponse<User> updateProfile(@RequestBody ProfileRequest req, @AuthenticationPrincipal CurrentUser user) {
        User u = profileService.updateProfile(req.email(), req.phone(), req.skills(), req.bio(), user);
        return ApiResponse.ok(u, "个人主页信息已保存");
    }

    /** POST /api/notifications/read-all：本人 */
    @PostMapping("/api/notifications/read-all")
    public ApiResponse<Boolean> markAllRead(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(profileService.markAllRead(user), "已全部标为已读");
    }
}
