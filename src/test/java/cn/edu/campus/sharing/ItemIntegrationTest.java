package cn.edu.campus.sharing;

import cn.edu.campus.sharing.item.*;
import cn.edu.campus.sharing.security.SessionUser;
import cn.edu.campus.sharing.user.*;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ItemIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired ItemMapper items;
    @Autowired JdbcTemplate jdbc;
    long owner, buyer, admin;

    @BeforeEach
    void accounts() {
        owner = account("13820000001", "STUDENT");
        buyer = account("13820000002", "STUDENT");
        admin = account("13820000003", "ADMIN");
    }

    @Test
    void pendingItemAndImagesArePrivateUntilApproved() throws Exception {
        JsonNode item = created(form(1, "高数书", null));
        long id = item.path("id").asLong();
        String image = item.path("images").get(0).path("url").asText();
        assertThat(item.path("reviewStatus").asText()).isEqualTo("PENDING");
        assertThat(item.path("images").get(0).has("filePath")).isFalse();
        mvc.perform(get("/api/items/" + id).with(actor(buyer))).andExpect(status().isNotFound());
        mvc.perform(get(image).with(actor(buyer))).andExpect(status().isNotFound());
        mvc.perform(get(image).with(actor(owner))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/items/" + id).with(actor(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/items").with(actor(buyer))).andExpect(jsonPath("$.data.total").value(0));
        approve(id).andExpect(status().isOk());
        mvc.perform(get("/api/items/" + id).with(actor(buyer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactInfo").value("微信 campus"));
        mvc.perform(get(image).with(actor(buyer))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/items").with(actor(buyer))).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void authenticationCsrfAndAdminRoleAreEnforced() throws Exception {
        long id = created(form(1, "书", null)).path("id").asLong();
        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/items/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/items/" + id).with(actor(owner))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/items/" + id + "/approve").with(actor(owner)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/items").with(actor(buyer))).andExpect(status().isForbidden());
    }

    @Test
    void categoryPrecisionValidationAndMassAssignmentAreRejected() throws Exception {
        for (ItemRequest request : List.of(form(3, "工具", null), form(1, "书", "NORMAL"),
                form(999, "书", null), form(1, " ", null), form(3, "工具", "INVALID"))) {
            upload(request, png()).andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/items").file(png()).param("item",
                "{\"categoryId\":1,\"title\":\"书\",\"description\":\"内容\",\"contactInfo\":\"微信\",\"ownerId\":999}")
                .with(actor(owner)).with(csrf())).andExpect(status().isBadRequest());
        upload(form(3, "工具", "MINOR_ERROR"), png()).andExpect(status().isCreated());
    }

    @Test
    void imagesMustBeOneOrTwoRealMatchingPngOrJpegFiles() throws Exception {
        ItemRequest request = form(1, "书", null);
        upload(request).andExpect(status().isBadRequest());
        upload(request, png(), png(), png()).andExpect(status().isBadRequest());
        for (MockMultipartFile invalid : List.of(
                new MockMultipartFile("files", "x.png", "image/png", new byte[0]),
                new MockMultipartFile("files", "x.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("files", "x.png", "text/html", png().getBytes()),
                new MockMultipartFile("files", "x.jpg", "image/jpeg", png().getBytes()),
                new MockMultipartFile("files", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
                picture("png", 4097, 1))) {
            upload(request, invalid).andExpect(status().isBadRequest());
        }
        upload(request, png(), picture("jpeg", 2, 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images.length()").value(2));
    }

    @Test
    void nonOwnerCannotEditReplaceOrDelete() throws Exception {
        long id = created(form(1, "书", null)).path("id").asLong();
        mvc.perform(patch("/api/items/" + id).with(actor(buyer)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(form(1, "修改", null))))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/items/" + id + "/images").file(png()).with(actor(buyer)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/items/" + id).with(actor(buyer)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/items/999999").with(actor(owner))).andExpect(status().isNotFound());
    }

    @Test
    void rejectionRequiresReasonAndEditingResubmitsAndClearsNullableFields() throws Exception {
        long id = created(form(3, "工具", "NORMAL")).path("id").asLong();
        reject(id, " ").andExpect(status().isBadRequest());
        reject(id, "请补充描述").andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewReason").value("请补充描述"));
        mvc.perform(get("/api/users/me/items").param("reviewStatus", "REJECTED").with(actor(owner)))
                .andExpect(jsonPath("$.data.total").value(1));
        update(id, form(1, "工具说明书", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.reviewReason").doesNotExist())
                .andExpect(jsonPath("$.data.toolCondition").doesNotExist());
        assertThat(items.selectById(id).getToolCondition()).isNull();
        approve(id).andExpect(status().isOk());
        update(id, form(1, "修改后的说明书", null)).andExpect(status().isOk());
        mvc.perform(get("/api/items/" + id).with(actor(buyer))).andExpect(status().isNotFound());
    }

    @Test
    void replacingImagesInvalidatesOldUrlsAndRequiresReviewAgain() throws Exception {
        JsonNode item = created(form(1, "书", null));
        long id = item.path("id").asLong();
        String oldUrl = item.path("images").get(0).path("url").asText();
        approve(id).andExpect(status().isOk());
        JsonNode replaced = data(mvc.perform(multipart("/api/items/" + id + "/images")
                .file(png()).file(png()).with(actor(owner)).with(csrf())).andExpect(status().isOk()));
        assertThat(replaced.path("reviewStatus").asText()).isEqualTo("PENDING");
        assertThat(replaced.path("images").size()).isEqualTo(2);
        mvc.perform(get(oldUrl).with(actor(owner))).andExpect(status().isNotFound());
        mvc.perform(get(replaced.path("images").get(0).path("url").asText()).with(actor(buyer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicSearchEscapesWildcardsAndPaginatesWhileMineIncludesPending() throws Exception {
        long a = created(form(1, "100%_好书", null)).path("id").asLong();
        long b = created(form(2, "杯子", null)).path("id").asLong();
        created(form(1, "未审核", null));
        approve(a).andExpect(status().isOk()); approve(b).andExpect(status().isOk());
        mvc.perform(get("/api/items").param("keyword", "%_").with(actor(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/items").param("categoryId", "2").with(actor(buyer)))
                .andExpect(jsonPath("$.data.items[0].id").value(b));
        mvc.perform(get("/api/items").param("size", "1").param("page", "2").with(actor(buyer)))
                .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items[0].id").value(a));
        mvc.perform(get("/api/users/me/items").with(actor(owner))).andExpect(jsonPath("$.data.total").value(3));
        mvc.perform(get("/api/users/me/items").with(actor(buyer))).andExpect(jsonPath("$.data.total").value(0));
        for (String query : List.of("page=0", "size=101", "categoryId=999", "status=BAD")) {
            mvc.perform(get("/api/items?" + query).with(actor(buyer))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/items").param("reviewStatus", "PENDING").with(actor(admin)))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void softDeletionHidesImagesAndKeepsAuditAndRow() throws Exception {
        JsonNode item = created(form(1, "书", null));
        long id = item.path("id").asLong();
        mvc.perform(delete("/api/admin/items/" + id).with(actor(admin)).with(csrf())).andExpect(status().isOk());
        assertThat(items.selectById(id).getDeleted()).isTrue();
        mvc.perform(get("/api/items/" + id).with(actor(admin))).andExpect(status().isNotFound());
        mvc.perform(get(item.path("images").get(0).path("url").asText()).with(actor(owner))).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/me/items").with(actor(owner))).andExpect(jsonPath("$.data.total").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE action='DELETE_ITEM' AND target_id=?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void bannedOwnersCannotPublishAndTheirApprovedItemsAreHidden() throws Exception {
        long id = created(form(1, "书", null)).path("id").asLong();
        approve(id).andExpect(status().isOk());
        users.update(null, new UpdateWrapper<UserAccount>().eq("id", owner).set("status", "BANNED"));
        upload(form(1, "新书", null), png()).andExpect(status().isForbidden());
        mvc.perform(get("/api/items").with(actor(buyer))).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/items/" + id).with(actor(buyer))).andExpect(status().isNotFound());
    }

    @Test
    void reservedAndCompletedItemsCannotBeChangedByOwner() throws Exception {
        long id = created(form(1, "书", null)).path("id").asLong();
        for (String state : List.of("RESERVED", "COMPLETED")) {
            items.update(null, new UpdateWrapper<Item>().eq("id", id).set("status", state));
            update(id, form(1, "修改", null)).andExpect(status().isConflict());
            mvc.perform(multipart("/api/items/" + id + "/images").file(png()).with(actor(owner)).with(csrf()))
                    .andExpect(status().isConflict());
            mvc.perform(delete("/api/items/" + id).with(actor(owner)).with(csrf())).andExpect(status().isConflict());
        }
    }

    @Test
    void reviewCannotBeReplayedAndAuditIsWrittenOnce() throws Exception {
        long id = created(form(1, "书", null)).path("id").asLong();
        approve(id).andExpect(status().isOk());
        approve(id).andExpect(status().isConflict());
        reject(id, "重复审核").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE target_type='ITEM' AND target_id=?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void failedSecondImageRollsBackFirstFileAndDatabaseRows() throws Exception {
        Set<String> before = storedFiles();
        upload(form(1, "书", null), png(), new MockMultipartFile("files", "bad.png", "image/png", new byte[]{1, 2}))
                .andExpect(status().isBadRequest());
        TestTransaction.flagForRollback(); TestTransaction.end();
        assertThat(storedFiles()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM items", Long.class)).isZero();
    }

    private Set<String> storedFiles() throws Exception {
        Path root = Path.of("target/test-uploads");
        if (!Files.exists(root)) return Set.of();
        try (var paths = Files.list(root)) { return paths.map(Path::toString).collect(java.util.stream.Collectors.toSet()); }
    }
    private long account(String phone, String role) {
        UserAccount user = new UserAccount();
        user.setPhone(phone); user.setPasswordHash("unused-fixture-hash"); user.setNickname("测试学生");
        user.setContactInfo("微信"); user.setRole(role); user.setStatus("ACTIVE"); user.setCreatedAt(LocalDateTime.now());
        users.insert(user); return user.getId();
    }
    private RequestPostProcessor actor(long id) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new SessionUser(id), null,
                List.of(new SimpleGrantedAuthority(id == admin ? "ROLE_ADMIN" : "ROLE_STUDENT"))));
    }
    private ItemRequest form(long category, String title, String condition) {
        return new ItemRequest(category, title, "九成新，适合校园使用", "微信 campus", condition);
    }
    private MockMultipartFile png() throws Exception { return picture("png", 2, 2); }
    private MockMultipartFile picture(String format, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
        return new MockMultipartFile("files", "../../fixture." + format, "image/" + format, output.toByteArray());
    }
    private ResultActions upload(ItemRequest form, MockMultipartFile... files) throws Exception {
        var request = multipart("/api/items");
        request.param("item", json.writeValueAsString(form));
        for (var file : files) request.file(file);
        return mvc.perform(request.with(actor(owner)).with(csrf()));
    }
    private JsonNode created(ItemRequest form) throws Exception { return data(upload(form, png()).andExpect(status().isCreated())); }
    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }
    private ResultActions approve(long id) throws Exception {
        return mvc.perform(post("/api/admin/items/" + id + "/approve").with(actor(admin)).with(csrf()));
    }
    private ResultActions reject(long id, String reason) throws Exception {
        return mvc.perform(post("/api/admin/items/" + id + "/reject").with(actor(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("reason", reason))));
    }
    private ResultActions update(long id, ItemRequest form) throws Exception {
        return mvc.perform(patch("/api/items/" + id).with(actor(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(form)));
    }
}
