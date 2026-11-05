package cn.edu.campus.sharing.category;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoryService {
    private final CategoryMapper mapper;

    public CategoryService(CategoryMapper mapper) { this.mapper = mapper; }

    public List<CategoryResponse> list() {
        return mapper.selectList(new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId))
                .stream().map(c -> new CategoryResponse(c.getId(), c.getName())).toList();
    }

    public record CategoryResponse(Long id, String name) {}
}
