package cn.edu.campus.sharing.item;

import cn.edu.campus.sharing.common.*;
import cn.edu.campus.sharing.security.SessionUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Validated
@RestController
public class ItemController {
    private final ItemService service;
    private final ObjectMapper json;
    private final Validator validator;

    public ItemController(ItemService service, ObjectMapper json, Validator validator) {
        this.service = service;
        this.json = json;
        this.validator = validator;
    }

    @PostMapping(value = "/api/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemResponse>> create(@AuthenticationPrincipal SessionUser actor,
            @RequestParam("item") String form, @RequestParam("files") List<MultipartFile> files) {
        ItemRequest request;
        try { request = json.readValue(form, ItemRequest.class); }
        catch (JsonProcessingException ex) { throw new BusinessException(HttpStatus.BAD_REQUEST, "item 字段必须为合法的物品 JSON"); }
        if (request == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "物品信息不能为空");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw new BusinessException(HttpStatus.BAD_REQUEST, first.getPropertyPath() + ": " + first.getMessage());
        }
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(actor.id(), request, files)));
    }

    @PatchMapping("/api/items/{id}")
    public ApiResponse<ItemResponse> update(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id,
                                             @Valid @RequestBody ItemRequest request) {
        return ApiResponse.ok(service.update(actor.id(), id, request));
    }

    @PostMapping(value = "/api/items/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ItemResponse> images(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id,
                                             @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.ok(service.replaceImages(actor.id(), id, files));
    }

    @DeleteMapping("/api/items/{id}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id) {
        service.remove(actor.id(), id, false);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/items/{id}")
    public ApiResponse<ItemResponse> detail(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id) {
        return ApiResponse.ok(service.detail(actor.id(), id));
    }

    @GetMapping("/api/items/{id}/images/{imageId}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal SessionUser actor, @PathVariable @Positive long id,
                                         @PathVariable @Positive long imageId) {
        var image = service.image(actor.id(), id, imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore()).body(image.bytes());
    }

    @GetMapping("/api/items")
    public ApiResponse<PageResponse<ItemResponse>> list(@AuthenticationPrincipal SessionUser actor,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(required = false) @Pattern(regexp = "AVAILABLE|RESERVED|COMPLETED") String status) {
        return ApiResponse.ok(service.list(actor.id(), page, size, categoryId, keyword, status, null, false, false));
    }

    @GetMapping("/api/users/me/items")
    public ApiResponse<PageResponse<ItemResponse>> mine(@AuthenticationPrincipal SessionUser actor,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Pattern(regexp = "PENDING|APPROVED|REJECTED") String reviewStatus) {
        return ApiResponse.ok(service.list(actor.id(), page, size, null, "", null, reviewStatus, true, false));
    }
}
