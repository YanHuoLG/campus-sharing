package cn.edu.campus.sharing.admin;

import cn.edu.campus.sharing.common.*;
import cn.edu.campus.sharing.item.*;
import cn.edu.campus.sharing.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/items")
public class AdminItemController {
    private final ItemService service;

    public AdminItemController(ItemService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResponse<ItemResponse>> list(@AuthenticationPrincipal SessionUser admin,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(required = false) @Pattern(regexp = "PENDING|APPROVED|REJECTED") String reviewStatus,
            @RequestParam(required = false) @Pattern(regexp = "AVAILABLE|RESERVED|COMPLETED") String status) {
        return ApiResponse.ok(service.list(admin.id(), page, size, categoryId, keyword, status, reviewStatus, false, true));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ItemResponse> approve(@AuthenticationPrincipal SessionUser admin, @PathVariable @Positive long id) {
        return ApiResponse.ok(service.review(admin.id(), id, null, true));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ItemResponse> reject(@AuthenticationPrincipal SessionUser admin, @PathVariable @Positive long id,
                                             @Valid @RequestBody RejectItemRequest request) {
        return ApiResponse.ok(service.review(admin.id(), id, request.reason(), false));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal SessionUser admin, @PathVariable @Positive long id) {
        service.remove(admin.id(), id, true);
        return ApiResponse.ok(null);
    }
}
