package cn.edu.campus.sharing.admin;

import cn.edu.campus.sharing.common.ApiResponse;
import cn.edu.campus.sharing.common.PageResponse;
import cn.edu.campus.sharing.security.SessionUser;
import cn.edu.campus.sharing.user.UserResponse;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") @Size(max = 60) String keyword) {
        return ApiResponse.ok(service.list(page, size, keyword));
    }

    @PostMapping("/{id}/ban")
    public ApiResponse<UserResponse> ban(@AuthenticationPrincipal SessionUser admin,
                                         @PathVariable @Positive long id) {
        return ApiResponse.ok(service.setBanned(admin.id(), id, true));
    }

    @PostMapping("/{id}/unban")
    public ApiResponse<UserResponse> unban(@AuthenticationPrincipal SessionUser admin,
                                           @PathVariable @Positive long id) {
        return ApiResponse.ok(service.setBanned(admin.id(), id, false));
    }
}
