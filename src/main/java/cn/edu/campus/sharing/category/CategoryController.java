package cn.edu.campus.sharing.category;

import cn.edu.campus.sharing.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryService.CategoryResponse>> list() {
        return ApiResponse.ok(service.list());
    }
}
