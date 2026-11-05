package cn.edu.campus.sharing;

import cn.edu.campus.sharing.common.BusinessException;
import cn.edu.campus.sharing.item.*;
import cn.edu.campus.sharing.reservation.*;
import cn.edu.campus.sharing.security.SessionUser;
import cn.edu.campus.sharing.user.*;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired ItemMapper items;
    @Autowired HandoverMapper handovers;
    @Autowired ReservationService service;
    @Autowired ItemService itemService;
    @Autowired PasswordEncoder hashes;
    @Autowired JdbcTemplate jdbc;
    long owner, buyer, other, admin, itemId;

    @BeforeEach
    void fixtures() {
        owner = account("13830000001", "STUDENT"); buyer = account("13830000002", "STUDENT");
        other = account("13830000003", "STUDENT"); admin = account("13830000004", "ADMIN");
        Item item = new Item();
        item.setOwnerId(owner); item.setCategoryId(1L); item.setTitle("预约测试教材");
        item.setDescription("九成新"); item.setContactInfo("微信 test"); item.setReviewStatus("APPROVED");
        item.setStatus("AVAILABLE"); item.setDeleted(false); item.setCreatedAt(LocalDateTime.now()); item.setUpdatedAt(LocalDateTime.now());
        items.insert(item); itemId = item.getId();
    }

    @AfterEach
    void cleanup() {
        // Also cleans the explicitly committed fixtures used by concurrent/commit tests.
        jdbc.update("DELETE FROM handover_records WHERE reservation_id IN (SELECT id FROM reservations WHERE item_id=?)", itemId);
        jdbc.update("DELETE FROM reservations WHERE item_id=?", itemId);
        jdbc.update("DELETE FROM admin_audit_logs WHERE target_type='ITEM' AND target_id=?", itemId);
        jdbc.update("DELETE FROM item_images WHERE item_id=?", itemId);
        jdbc.update("DELETE FROM items WHERE id=?", itemId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?,?)", owner, buyer, other, admin);
    }

    @Test
    void createValidatesOwnershipDuplicatesAndInput() throws Exception {
        create(owner, "自己的物品").andExpect(status().isConflict());
        create(buyer, " ").andExpect(status().isBadRequest());
        create(buyer, "字".repeat(501)).andExpect(status().isBadRequest());
        send(buyer, post("/api/items/" + itemId + "/reservations"), Map.of("message", "留言", "status", "ACCEPTED"))
                .andExpect(status().isBadRequest());
        create(buyer, "明天下午取").andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"));
        create(buyer, "重复申请").andExpect(status().isConflict());
        create(other, "我也想要").andExpect(status().isCreated());
    }

    @Test
    void unapprovedDeletedAndBannedAccountsCannotReserve() throws Exception {
        for (String review : List.of("PENDING", "REJECTED")) {
            items.update(null, new UpdateWrapper<Item>().eq("id", itemId).set("review_status", review));
            create(buyer, "申请").andExpect(status().isNotFound());
        }
        items.update(null, new UpdateWrapper<Item>().eq("id", itemId).set("review_status", "APPROVED").set("deleted", true));
        create(buyer, "申请").andExpect(status().isNotFound());
        items.update(null, new UpdateWrapper<Item>().eq("id", itemId).set("deleted", false));
        users.update(null, new UpdateWrapper<UserAccount>().eq("id", owner).set("status", "BANNED"));
        create(buyer, "申请").andExpect(status().isForbidden());
        users.update(null, new UpdateWrapper<UserAccount>().eq("id", buyer).set("status", "BANNED"));
        create(buyer, "申请").andExpect(status().isForbidden());
    }

    @Test
    void acceptingClaimsItemRejectsOthersAndStoresOnlyHash() throws Exception {
        long first = reservation(buyer), second = reservation(other);
        JsonNode result = data(action(owner, first, "accept", null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")));
        String code = result.path("code").asText();
        assertThat(code).matches("[0-9]{6}");
        assertThat(LocalDateTime.parse(result.path("expiresAt").asText()).getNano()).isZero();
        assertThat(result.path("expiresAt").asText()).isEqualTo(result.path("reservation").path("expiresAt").asText());
        assertThat(hashes.matches(code, handovers.lockForReservation(first).getCodeHash())).isTrue();
        assertThat(state(first)).isEqualTo("ACCEPTED"); assertThat(state(second)).isEqualTo("REJECTED");
        assertThat(items.selectById(itemId).getStatus()).isEqualTo("RESERVED");
        action(owner, second, "accept", null).andExpect(status().isConflict());
        action(owner, first, "accept", null).andExpect(status().isConflict());
        create(other, "再次申请").andExpect(status().isConflict());
        send(buyer, get("/api/reservations/" + first), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codeHash").doesNotExist()).andExpect(jsonPath("$.data.code").doesNotExist());
    }

    @Test
    void onlyOwnerCanAcceptRejectAndOnlyParticipantsCanReadOrComplete() throws Exception {
        long id = reservation(buyer);
        action(buyer, id, "accept", null).andExpect(status().isForbidden());
        action(buyer, id, "reject", null).andExpect(status().isForbidden());
        for (long outsider : List.of(other, admin)) {
            send(outsider, get("/api/reservations/" + id), null).andExpect(status().isForbidden());
            for (String operation : List.of("accept", "reject", "cancel", "handover-code", "complete")) {
                action(outsider, id, operation, operation.equals("complete") ? Map.of("code", "000000") : null)
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void pendingRejectAndRequesterCancelPermitFreshApplications() throws Exception {
        long id = reservation(buyer);
        action(owner, id, "cancel", null).andExpect(status().isForbidden());
        action(owner, id, "reject", null).andExpect(status().isOk());
        long next = reservation(buyer);
        action(buyer, next, "cancel", null).andExpect(status().isOk());
        action(buyer, next, "cancel", null).andExpect(status().isConflict());
        create(buyer, "重新申请").andExpect(status().isCreated());
    }

    @Test
    void eitherParticipantCanCancelAcceptedReservationAndReleaseItem() throws Exception {
        for (long actor : List.of(owner, buyer)) {
            long id = reservation(buyer);
            String code = accept(id);
            action(actor, id, "cancel", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
            assertThat(items.selectById(itemId).getStatus()).isEqualTo("AVAILABLE");
            complete(buyer, id, code).andExpect(status().isConflict());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void incorrectAttemptsCommitAndLimitExpiresReservation() throws Exception {
        long id = reservation(buyer);
        String code = accept(id), wrong = code.equals("000000") ? "999999" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            complete(buyer, id, wrong).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT attempts FROM handover_records WHERE reservation_id=?", Integer.class, id)).isEqualTo(attempt);
        }
        assertThat(state(id)).isEqualTo("EXPIRED");
        assertThat(items.selectById(itemId).getStatus()).isEqualTo("AVAILABLE");
        complete(owner, id, code).andExpect(status().isConflict());
        action(buyer, id, "handover-code", null).andExpect(status().isConflict());
    }

    @Test
    void codeRotationInvalidatesOldCodePreservesDeadlineAndAttempts() throws Exception {
        long id = reservation(buyer);
        String oldCode = accept(id);
        HandoverRecord previous = handovers.lockForReservation(id);
        complete(buyer, id, oldCode.equals("000000") ? "999999" : "000000").andExpect(status().isConflict());
        String next = data(action(buyer, id, "handover-code", null).andExpect(status().isOk())).path("code").asText();
        assertThat(next).isNotEqualTo(oldCode);
        HandoverRecord current = handovers.lockForReservation(id);
        assertThat(current.getExpiresAt()).isEqualTo(previous.getExpiresAt());
        assertThat(current.getAttempts()).isEqualTo(1);
        complete(owner, id, oldCode).andExpect(status().isConflict());
        complete(owner, id, next).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(items.selectById(itemId).getStatus()).isEqualTo("COMPLETED");
        assertThat(handovers.lockForReservation(id).getCompletedAt()).isNotNull();
        complete(buyer, id, next).andExpect(status().isConflict());
        action(buyer, id, "cancel", null).andExpect(status().isConflict());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void expiredCodeFailsAndReleasesItemEvenWhenReturningConflict() throws Exception {
        long id = reservation(buyer);
        String code = accept(id);
        pastDeadline(id);
        complete(buyer, id, code).andExpect(status().isConflict());
        assertThat(state(id)).isEqualTo("EXPIRED");
        assertThat(items.selectById(itemId).getStatus()).isEqualTo("AVAILABLE");
        create(other, "过期后重新预约").andExpect(status().isCreated());
    }

    @Test
    void expiryWorkerIsIdempotentAndDoesNotExpireFreshReservations() throws Exception {
        long id = reservation(buyer); accept(id);
        service.expire(id); assertThat(state(id)).isEqualTo("ACCEPTED");
        pastDeadline(id); assertThat(service.due()).contains(id);
        service.expire(id); service.expire(id);
        assertThat(state(id)).isEqualTo("EXPIRED");
        assertThat(items.selectById(itemId).getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void newApplicationReleasesExpiredOccupancyBeforeSchedulerRuns() throws Exception {
        long id = reservation(buyer); accept(id); pastDeadline(id);
        create(other, "预约已过期物品").andExpect(status().isCreated());
        assertThat(state(id)).isEqualTo("EXPIRED");
        assertThat(items.selectById(itemId).getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void bannedCounterpartyBlocksAcceptanceAndHandoverButAllowsCancellation() throws Exception {
        long id = reservation(buyer);
        users.update(null, new UpdateWrapper<UserAccount>().eq("id", buyer).set("status", "BANNED"));
        action(owner, id, "accept", null).andExpect(status().isForbidden());
        users.update(null, new UpdateWrapper<UserAccount>().eq("id", buyer).set("status", "ACTIVE"));
        String code = accept(id);
        users.update(null, new UpdateWrapper<UserAccount>().eq("id", buyer).set("status", "BANNED"));
        complete(owner, id, code).andExpect(status().isForbidden());
        action(owner, id, "handover-code", null).andExpect(status().isForbidden());
        action(owner, id, "cancel", null).andExpect(status().isOk());
    }

    @Test
    void itemEditsCancelPendingRequestsAndAdminRemovalCancelsAccepted() throws Exception {
        long pending = reservation(buyer);
        itemService.update(owner, itemId, new ItemRequest(1L, "已修改", "新说明", "微信", null));
        assertThat(state(pending)).isEqualTo("CANCELLED");
        action(owner, pending, "accept", null).andExpect(status().isConflict());
        items.update(null, new UpdateWrapper<Item>().eq("id", itemId).set("review_status", "APPROVED"));
        long accepted = reservation(buyer); String code = accept(accepted);
        send(admin, delete("/api/admin/items/" + itemId), null).andExpect(status().isOk());
        assertThat(state(accepted)).isEqualTo("CANCELLED");
        assertThat(items.selectById(itemId).getDeleted()).isTrue();
        complete(buyer, accepted, code).andExpect(status().isConflict());
        send(buyer, get("/api/reservations/" + accepted), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemDeleted").value(true));
    }

    @Test
    void listsFilterToParticipantPaginateAndDoNotExposeCredentials() throws Exception {
        reservation(buyer); reservation(other);
        send(owner, get("/api/users/me/item-reservations").param("size", "1").param("status", "PENDING"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].codeHash").doesNotExist());
        send(buyer, get("/api/users/me/reservations"), null).andExpect(jsonPath("$.data.total").value(1));
        send(buyer, get("/api/users/me/item-reservations"), null).andExpect(jsonPath("$.data.total").value(0));
        for (String query : List.of("page=0", "size=101", "status=BAD")) {
            send(owner, get("/api/users/me/item-reservations?" + query), null).andExpect(status().isBadRequest());
        }
    }

    @Test
    void authCsrfAndCodeFormatAreRequired() throws Exception {
        mvc.perform(get("/api/users/me/reservations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/items/" + itemId + "/reservations").with(authentication(identity(buyer)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"留言\"}")).andExpect(status().isForbidden());
        long id = reservation(buyer); accept(id);
        for (String code : List.of("12345", "1234567", "abcdef", "１２３４５６")) complete(buyer, id, code).andExpect(status().isBadRequest());
        assertThat(handovers.lockForReservation(id).getAttempts()).isZero();
        send(buyer, get("/api/reservations/999999"), null).andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAcceptsHaveExactlyOneWinner() throws Exception {
        long first = service.create(buyer, itemId, "申请一").id();
        long second = service.create(other, itemId, "申请二").id();
        assertThat(race(() -> service.accept(owner, first), () -> service.accept(owner, second))).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE item_id=? AND status='ACCEPTED'", Integer.class, itemId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_records WHERE reservation_id IN (?,?)", Integer.class, first, second)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCompletionsConsumeCodeOnlyOnce() throws Exception {
        long id = service.create(buyer, itemId, "申请").id(); String code = service.accept(owner, id).code();
        assertThat(race(() -> service.complete(owner, id, code), () -> service.complete(buyer, id, code))).containsExactlyInAnyOrder(200, 409);
        assertThat(state(id)).isEqualTo("COMPLETED");
        assertThat(items.selectById(itemId).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateApplicationsCreateOneRow() throws Exception {
        assertThat(race(() -> service.create(buyer, itemId, "一"), () -> service.create(buyer, itemId, "二"))).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE item_id=?", Integer.class, itemId)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancellationRacingCompletionLeavesConsistentTerminalState() throws Exception {
        long id = service.create(buyer, itemId, "申请").id(); String code = service.accept(owner, id).code();
        assertThat(race(() -> service.cancel(owner, id), () -> service.complete(buyer, id, code))).containsExactlyInAnyOrder(200, 409);
        assertThat(items.selectById(itemId).getStatus()).isEqualTo(state(id).equals("COMPLETED") ? "COMPLETED" : "AVAILABLE");
    }

    private List<Integer> race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) futures.add(executor.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                try { task.call(); return 200; } catch (BusinessException ex) { return ex.getStatus().value(); }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }
    private long account(String phone, String role) {
        UserAccount user = new UserAccount(); user.setPhone(phone); user.setRole(role); user.setStatus("ACTIVE");
        user.setNickname("预约测试"); user.setContactInfo("微信"); user.setPasswordHash("fixture-only"); user.setCreatedAt(LocalDateTime.now());
        users.insert(user); return user.getId();
    }
    private UsernamePasswordAuthenticationToken identity(long actor) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionUser(actor), null,
                List.of(new SimpleGrantedAuthority(actor == admin ? "ROLE_ADMIN" : "ROLE_STUDENT")));
    }
    private ResultActions send(long actor, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(request.with(authentication(identity(actor))).with(csrf()));
    }
    private ResultActions create(long actor, String message) throws Exception { return send(actor, post("/api/items/" + itemId + "/reservations"), Map.of("message", message)); }
    private long reservation(long actor) throws Exception { return data(create(actor, "周末可以取吗").andExpect(status().isCreated())).path("id").asLong(); }
    private ResultActions action(long actor, long id, String action, Object body) throws Exception { return send(actor, post("/api/reservations/" + id + "/" + action), body); }
    private String accept(long id) throws Exception { return data(action(owner, id, "accept", null).andExpect(status().isOk())).path("code").asText(); }
    private ResultActions complete(long actor, long id, String code) throws Exception { return action(actor, id, "complete", Map.of("code", code)); }
    private JsonNode data(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data"); }
    private String state(long id) { return jdbc.queryForObject("SELECT status FROM reservations WHERE id=?", String.class, id); }
    private void pastDeadline(long id) { handovers.update(null, new UpdateWrapper<HandoverRecord>().eq("reservation_id", id).set("expires_at", LocalDateTime.now().minusMinutes(1))); }
}
