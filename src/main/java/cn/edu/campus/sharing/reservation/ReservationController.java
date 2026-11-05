package cn.edu.campus.sharing.reservation;

import cn.edu.campus.sharing.common.*;
import cn.edu.campus.sharing.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) { this.service = service; }

    @PostMapping("/api/items/{id}/reservations")
    public ResponseEntity<ApiResponse<ReservationView>> create(@AuthenticationPrincipal SessionUser actor,
            @PathVariable @Positive long id, @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(actor.id(), id, request.message())));
    }
    @GetMapping("/api/reservations/{id}")
    public ApiResponse<ReservationView> detail(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id) {
        return ApiResponse.ok(service.detail(actor.id(), id));
    }
    @PostMapping("/api/reservations/{id}/accept")
    public ResponseEntity<ApiResponse<HandoverCodeResponse>> accept(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id) {
        return secret(service.accept(actor.id(), id));
    }
    @PostMapping("/api/reservations/{id}/reject")
    public ApiResponse<ReservationView> reject(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id) {
        return ApiResponse.ok(service.reject(actor.id(), id));
    }
    @PostMapping("/api/reservations/{id}/cancel")
    public ApiResponse<ReservationView> cancel(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id) {
        return ApiResponse.ok(service.cancel(actor.id(), id));
    }
    @PostMapping("/api/reservations/{id}/handover-code")
    public ResponseEntity<ApiResponse<HandoverCodeResponse>> code(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id) {
        return secret(service.regenerate(actor.id(), id));
    }
    @PostMapping("/api/reservations/{id}/complete")
    public ApiResponse<ReservationView> complete(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id,
            @Valid @RequestBody CompleteReservationRequest request) {
        return ApiResponse.ok(service.complete(actor.id(), id, request.code()));
    }
    @GetMapping("/api/users/me/reservations")
    public ApiResponse<PageResponse<ReservationView>> mine(@AuthenticationPrincipal SessionUser actor,
            @RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
            @RequestParam(required=false) @Pattern(regexp="PENDING|ACCEPTED|REJECTED|COMPLETED|CANCELLED|EXPIRED") String status) {
        return ApiResponse.ok(service.list(actor.id(), false, status, page, size));
    }
    @GetMapping("/api/users/me/item-reservations")
    public ApiResponse<PageResponse<ReservationView>> received(@AuthenticationPrincipal SessionUser actor,
            @RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
            @RequestParam(required=false) @Pattern(regexp="PENDING|ACCEPTED|REJECTED|COMPLETED|CANCELLED|EXPIRED") String status) {
        return ApiResponse.ok(service.list(actor.id(), true, status, page, size));
    }
    private ResponseEntity<ApiResponse<HandoverCodeResponse>> secret(HandoverCodeResponse response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(response));
    }
}
