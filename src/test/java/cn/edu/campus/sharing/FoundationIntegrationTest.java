package cn.edu.campus.sharing;

import cn.edu.campus.sharing.category.Category;
import cn.edu.campus.sharing.category.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryMapper categories;
    @Autowired JdbcTemplate jdbc;

    @Test
    void healthChecksDatabase() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.database").value("UP"));
    }

    @Test
    void categoriesAreOrderedDtos() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("学习资源"))
                .andExpect(jsonPath("$.data[1].name").value("生活用品"))
                .andExpect(jsonPath("$.data[2].name").value("专业工具"))
                .andExpect(jsonPath("$.data[0].sortOrder").doesNotExist());
    }

    @Test
    void privateEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/items")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser
    void unknownEndpointReturnsUnifiedNotFound() throws Exception {
        mvc.perform(get("/api/unknown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @Transactional
    void mybatisReadsAndWritesDatabase() {
        Category value = new Category();
        value.setName("集成测试分类");
        value.setSortOrder(99);
        assertThat(categories.insert(value)).isEqualTo(1);
        assertThat(value.getId()).isNotNull();
        assertThat(categories.selectById(value.getId()).getName()).isEqualTo("集成测试分类");
    }

    @Test
    void schemaRejectsInvalidRoles() {
        String sql = "INSERT INTO users(phone,password_hash,nickname,contact_info,role) VALUES (?,?,?,?,?)";
        assertThatThrownBy(() -> jdbc.update(sql, "13800000000", "not-a-real-password", "test", "test", "ROOT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void schemaRejectsDuplicatePhone() {
        String sql = "INSERT INTO users(phone,password_hash,nickname,contact_info) VALUES (?,?,?,?)";
        jdbc.update(sql, "13800000001", "test-hash", "学生甲", "test");
        assertThatThrownBy(() -> jdbc.update(sql, "13800000001", "test-hash", "学生乙", "test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
