package cn.edu.campus.sharing;

import cn.edu.campus.sharing.security.SessionUser;
import cn.edu.campus.sharing.user.UserMapper;
import cn.edu.campus.sharing.user.UserAccount;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {
    private static final String PASSWORD = "Test-pass-2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired UserMapper users;

    @Test
    void registrationHashesPasswordAndNeverReturnsSecrets() throws Exception {
        Client client = client();
        var result = register(client, "13900000001").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE phone='13900000001'", String.class);
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        assertThat(body(result).path("data").path("createdAt").asText()).isNotEmpty();
        mvc.perform(client.use(get("/api/users/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicatePhoneReturns409() throws Exception {
        Client client = client();
        register(client, "13900000002").andExpect(status().isCreated());
        register(client, "13900000002").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该手机号已注册"));
    }

    @Test
    void invalidRegistrationAndRoleInjectionReturn400() throws Exception {
        Client client = client();
        for (Map<String, String> request : java.util.List.of(
                Map.of("phone", "123", "password", PASSWORD, "nickname", "学生", "contactInfo", "微信"),
                Map.of("phone", "13900000003", "password", "123", "nickname", "学生", "contactInfo", "微信"),
                Map.of("phone", "13900000003", "password", PASSWORD, "nickname", " ", "contactInfo", "微信"),
                Map.of("phone", "13900000003", "password", "密".repeat(30), "nickname", "学生", "contactInfo", "微信"),
                Map.of("phone", "13900000003", "password", PASSWORD, "nickname", "学生", "contactInfo", "微信", "role", "ADMIN"))) {
            send(client, post("/api/auth/register"), request).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE phone='13900000003'", Long.class)).isZero();
    }

    @Test
    void loginRotatesSessionPersistsIdentityAndRefreshesCsrf() throws Exception {
        Client client = client();
        register(client, "13900000004").andExpect(status().isCreated());
        String oldId = client.session.getId();
        String oldToken = client.token;
        send(client, post("/api/auth/login"), credentials("13900000004", PASSWORD))
                .andExpect(status().isOk());
        assertThat(client.session.getId()).isNotEqualTo(oldId);
        SecurityContext context = (SecurityContext) client.session.getAttribute("SPRING_SECURITY_CONTEXT");
        if (context != null) {
            assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(SessionUser.class);
        }
        if (context != null) {
            assertThat(context.getAuthentication().getCredentials()).isNull();
        }
        mvc.perform(client.use(get("/api/users/me"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13900000004"));
        mvc.perform(client.use(patch("/api/users/me")).header("X-CSRF-TOKEN", oldToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"新昵称\"}"))
                .andExpect(status().isForbidden());
        client.refresh();
        send(client, patch("/api/users/me"), Map.of("nickname", "新昵称"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("新昵称"));
    }

    @Test
    void incorrectAndUnknownCredentialsReturnSame401() throws Exception {
        Client client = client();
        register(client, "13900000005").andExpect(status().isCreated());
        for (String phone : java.util.List.of("13900000005", "13900000999")) {
            send(client, post("/api/auth/login"), credentials(phone, "wrong-password"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("手机号或密码错误"));
        }
        mvc.perform(client.use(get("/api/users/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfIsRequiredForRegistrationLoginProfileAndLogout() throws Exception {
        Client client = client();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(registration("13900000006"))))
                .andExpect(status().isForbidden());
        register(client, "13900000006").andExpect(status().isCreated());
        mvc.perform(client.use(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(credentials("13900000006", PASSWORD))))
                .andExpect(status().isForbidden());
        login(client, "13900000006");
        mvc.perform(client.use(patch("/api/users/me")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"新昵称\"}")).andExpect(status().isForbidden());
        mvc.perform(client.use(post("/api/auth/logout"))).andExpect(status().isForbidden());
    }

    @Test
    void partialProfileUpdateKeepsOtherFieldsAndCannotChangeRoleOrPhone() throws Exception {
        Client client = client();
        register(client, "13900000007").andExpect(status().isCreated());
        login(client, "13900000007");
        send(client, patch("/api/users/me"), Map.of("nickname", "  新昵称  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.contactInfo").value("微信 test"));
        send(client, patch("/api/users/me"), Map.of("contactInfo", "新联系方式"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("新昵称"));
        for (Map<String, String> change : java.util.List.of(Map.<String, String>of(),
                Map.of("nickname", " "), Map.of("role", "ADMIN"), Map.of("phone", "13900000888"),
                Map.of("nickname", "超".repeat(41)))) {
            send(client, patch("/api/users/me"), change).andExpect(status().isBadRequest());
        }
    }

    @Test
    void logoutInvalidatesSessionAndClearsCookie() throws Exception {
        Client client = client();
        register(client, "13900000008").andExpect(status().isCreated());
        login(client, "13900000008");
        send(client, post("/api/auth/logout"), Map.of()).andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThat(client.session.isInvalid()).isTrue();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentsCannotListBanOrUnbanUsers() throws Exception {
        Client student = client();
        register(student, "13900000009").andExpect(status().isCreated());
        login(student, "13900000009");
        mvc.perform(student.use(get("/api/admin/users"))).andExpect(status().isForbidden());
        send(student, post("/api/admin/users/1/ban"), Map.of()).andExpect(status().isForbidden());
        send(student, post("/api/admin/users/1/unban"), Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void banRejectsExistingSessionAndLoginAndUnbanAllowsLogin() throws Exception {
        Client admin = admin("13900000010");
        Client student = client();
        long id = body(register(student, "13900000011")).path("data").path("id").asLong();
        login(student, "13900000011");
        send(admin, post("/api/admin/users/" + id + "/ban"), Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("BANNED"));
        send(admin, post("/api/admin/users/" + id + "/ban"), Map.of()).andExpect(status().isOk());
        send(student, patch("/api/users/me"), Map.of("nickname", "不应成功"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("账号已被封禁"));
        assertThat(student.session.isInvalid()).isTrue();
        Client banned = client();
        send(banned, post("/api/auth/login"), credentials("13900000011", PASSWORD))
                .andExpect(status().isForbidden());
        send(admin, post("/api/admin/users/" + id + "/unban"), Map.of()).andExpect(status().isOk());
        login(banned, "13900000011");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE target_id=?", Long.class, id)).isEqualTo(2);
    }

    @Test
    void adminCannotBanSelfOrOtherAdminAndMissingTargetIs404() throws Exception {
        Client admin = admin("13900000012");
        long id = jdbc.queryForObject("SELECT id FROM users WHERE phone='13900000012'", Long.class);
        send(admin, post("/api/admin/users/" + id + "/ban"), Map.of()).andExpect(status().isConflict());
        admin("13900000013");
        long otherId = jdbc.queryForObject("SELECT id FROM users WHERE phone='13900000013'", Long.class);
        send(admin, post("/api/admin/users/" + otherId + "/ban"), Map.of()).andExpect(status().isConflict());
        send(admin, post("/api/admin/users/999999/ban"), Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void roleChangesApplyToAlreadyLoggedInAdmin() throws Exception {
        Client admin = admin("13900000014");
        mvc.perform(admin.use(get("/api/admin/users"))).andExpect(status().isOk());
        users.update(null, new UpdateWrapper<UserAccount>().eq("phone", "13900000014").set("role", "STUDENT"));
        mvc.perform(admin.use(get("/api/admin/users"))).andExpect(status().isForbidden());
    }

    @Test
    void adminSearchPaginatesValidatesAndDoesNotLeakHashes() throws Exception {
        Client admin = admin("13900000015");
        register(client(), "13900000016").andExpect(status().isCreated());
        mvc.perform(admin.use(get("/api/admin/users").param("keyword", "13900000016")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].phone").value("13900000016"))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
        mvc.perform(admin.use(get("/api/admin/users").param("size", "1")))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(admin.use(get("/api/admin/users").param("page", "0"))).andExpect(status().isBadRequest());
        mvc.perform(admin.use(get("/api/admin/users").param("size", "101"))).andExpect(status().isBadRequest());
        mvc.perform(admin.use(get("/api/admin/users").param("keyword", "%")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    private Client admin(String phone) throws Exception {
        Client admin = client();
        register(admin, phone).andExpect(status().isCreated());
        users.update(null, new UpdateWrapper<UserAccount>().eq("phone", phone).set("role", "ADMIN"));
        login(admin, phone);
        return admin;
    }

    private ResultActions register(Client client, String phone) throws Exception {
        return send(client, post("/api/auth/register"), registration(phone));
    }

    private Map<String, String> registration(String phone) {
        return Map.of("phone", phone, "password", PASSWORD, "nickname", "测试学生", "contactInfo", "微信 test");
    }

    private Map<String, String> credentials(String phone, String password) {
        return Map.of("phone", phone, "password", password);
    }

    private void login(Client client, String phone) throws Exception {
        send(client, post("/api/auth/login"), credentials(phone, PASSWORD)).andExpect(status().isOk());
        client.refresh();
    }

    private ResultActions send(Client client, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(client.use(request).header("X-CSRF-TOKEN", client.token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private Client client() throws Exception {
        Client result = new Client();
        result.refresh();
        return result;
    }

    private class Client {
        MockHttpSession session;
        String token;

        MockHttpServletRequestBuilder use(MockHttpServletRequestBuilder request) {
            return session == null ? request : request.session(session);
        }

        void refresh() throws Exception {
            var result = mvc.perform(use(get("/api/auth/csrf"))).andExpect(status().isOk()).andReturn();
            session = (MockHttpSession) result.getRequest().getSession(false);
            token = json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
            assertThat(token).isNotEmpty();
        }
    }
}
