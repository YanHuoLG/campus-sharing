package cn.edu.campus.sharing.user;

import cn.edu.campus.sharing.common.ApiResponse;
import cn.edu.campus.sharing.security.SessionUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @GetMapping
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal SessionUser user) {
        return ApiResponse.ok(service.me(user.id()));
    }

    @PatchMapping
    public ApiResponse<UserResponse> update(@AuthenticationPrincipal SessionUser user,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(service.update(user.id(), request));
    }
}
