package cn.edu.campus.sharing.reservation;

import cn.edu.campus.sharing.common.*;
import cn.edu.campus.sharing.item.*;
import cn.edu.campus.sharing.user.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private final ReservationMapper reservations;
    private final HandoverMapper handovers;
    private final ItemMapper items;
    private final UserMapper users;
    private final UserService userService;
    private final PasswordEncoder hashes;
    private final Clock clock;
    private final long ttlSeconds;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();

    public ReservationService(ReservationMapper reservations, HandoverMapper handovers, ItemMapper items,
            UserMapper users, UserService userService, PasswordEncoder hashes, Clock clock,
            @Value("${app.handover.ttl-seconds:86400}") long ttlSeconds,
            @Value("${app.handover.max-attempts:5}") int maxAttempts) {
        if (ttlSeconds < 60 || ttlSeconds > 604800 || maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalArgumentException("Invalid handover limits");
        }
        this.reservations = reservations; this.handovers = handovers; this.items = items;
        this.users = users; this.userService = userService; this.hashes = hashes; this.clock = clock;
        this.ttlSeconds = ttlSeconds; this.maxAttempts = maxAttempts;
    }

    @Transactional
    public ReservationView create(long actorId, long itemId, String message) {
        Item seed = existingItem(items.selectById(itemId));
        if (seed.getOwnerId() == actorId) throw conflict("不能预约自己的物品");
        Map<Long, UserAccount> people = lockPeople(actorId, seed.getOwnerId());
        active(people.get(actorId)); active(people.get(seed.getOwnerId()));
        Item item = existingItem(items.lockById(itemId));
        expireForItem(item);
        if (Boolean.TRUE.equals(item.getDeleted()) || !"APPROVED".equals(item.getReviewStatus())) throw notFound();
        if (!"AVAILABLE".equals(item.getStatus())) throw conflict("物品当前不可预约");
        if (!reservations.activeRequests(itemId, actorId).isEmpty()) throw conflict("已提交有效预约，请勿重复申请");
        Reservation reservation = new Reservation();
        reservation.setItemId(itemId); reservation.setRequesterId(actorId); reservation.setMessage(message.strip());
        reservation.setStatus("PENDING"); reservation.setCreatedAt(now()); reservation.setUpdatedAt(now());
        reservations.insert(reservation);
        log.info("Reservation {} created for item {}", reservation.getId(), itemId);
        return reservations.view(reservation.getId());
    }

    @Transactional
    public HandoverCodeResponse accept(long actorId, long id) {
        Context context = lockContext(actorId, id);
        owner(actorId, context);
        active(context.requester());
        Reservation reservation = context.reservation();
        Item item = context.item();
        if (!"PENDING".equals(reservation.getStatus())) throw conflict("只能接受待确认预约");
        if (items.transition(item.getId(), "AVAILABLE", "RESERVED") != 1) throw conflict("物品已被占用或不可预约");
        transition(reservation, "ACCEPTED");
        // All decisions for this item are serialized by its row lock.
        reservations.closePending(item.getId(), "REJECTED");
        String code = newCode();
        HandoverRecord handover = new HandoverRecord();
        handover.setReservationId(id); handover.setCodeHash(hashes.encode(code));
        handover.setExpiresAt(now().plusSeconds(ttlSeconds)); handover.setAttempts(0);
        handovers.insert(handover);
        return new HandoverCodeResponse(reservations.view(id), code, handover.getExpiresAt());
    }

    @Transactional
    public ReservationView reject(long actorId, long id) {
        Context context = lockContext(actorId, id);
        owner(actorId, context);
        if (!"PENDING".equals(context.reservation().getStatus())) throw conflict("只能拒绝待确认预约");
        transition(context.reservation(), "REJECTED");
        return reservations.view(id);
    }

    @Transactional(noRollbackFor = CommittedReservationException.class)
    public ReservationView cancel(long actorId, long id) {
        Context context = lockContext(actorId, id);
        Reservation reservation = context.reservation();
        if ("PENDING".equals(reservation.getStatus())) {
            if (reservation.getRequesterId() != actorId) throw forbidden();
            transition(reservation, "CANCELLED");
        } else {
            HandoverRecord handover = acceptedHandover(context);
            ensureNotExpired(context, handover);
            release(context, "CANCELLED");
        }
        return reservations.view(id);
    }

    @Transactional(noRollbackFor = CommittedReservationException.class)
    public HandoverCodeResponse regenerate(long actorId, long id) {
        Context context = lockContext(actorId, id);
        active(context.owner()); active(context.requester());
        HandoverRecord handover = acceptedHandover(context);
        ensureNotExpired(context, handover);
        String code;
        do { code = newCode(); } while (hashes.matches(code, handover.getCodeHash()));
        handover.setCodeHash(hashes.encode(code));
        // Rotation must not reset the attempt budget or extend the reservation deadline.
        handovers.updateById(handover);
        return new HandoverCodeResponse(reservations.view(id), code, handover.getExpiresAt());
    }

    @Transactional(noRollbackFor = CommittedReservationException.class)
    public ReservationView complete(long actorId, long id, String code) {
        Context context = lockContext(actorId, id);
        active(context.owner()); active(context.requester());
        HandoverRecord handover = acceptedHandover(context);
        ensureNotExpired(context, handover);
        boolean matches = hashes.matches(code, handover.getCodeHash());
        ensureNotExpired(context, handover);
        if (!matches) {
            handover.setAttempts(handover.getAttempts() + 1);
            handovers.updateById(handover);
            if (handover.getAttempts() >= maxAttempts) {
                release(context, "EXPIRED");
                throw new CommittedReservationException("交接码错误次数达到上限，预约已失效，物品已释放");
            }
            throw new CommittedReservationException("交接码错误，剩余尝试次数：" + (maxAttempts - handover.getAttempts()));
        }
        if (items.transition(context.item().getId(), "RESERVED", "COMPLETED") != 1) throw conflict("物品状态已发生变化");
        transition(context.reservation(), "COMPLETED");
        handover.setCompletedAt(now()); handovers.updateById(handover);
        return reservations.view(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationView> list(long actorId, boolean received, String status, int page, int size) {
        userService.requireActive(actorId);
        return new PageResponse<>(reservations.search(actorId, received, status, size, ((long) page - 1) * size),
                page, size, reservations.countSearch(actorId, received, status));
    }

    @Transactional(readOnly = true)
    public ReservationView detail(long actorId, long id) {
        userService.requireActive(actorId);
        ReservationView view = reservations.view(id);
        if (view == null) throw notFound();
        if (view.ownerId() != actorId && view.requesterId() != actorId) throw forbidden();
        return view;
    }

    public List<Long> due() { return reservations.due(now()); }

    @Transactional
    public void expire(long id) {
        Reservation seed = reservations.selectById(id);
        if (seed == null) return;
        Item item = existingItem(items.lockById(seed.getItemId()));
        Reservation reservation = reservations.lockById(id);
        if (reservation == null || !"ACCEPTED".equals(reservation.getStatus())) return;
        HandoverRecord handover = handovers.lockForReservation(id);
        if (handover != null && !handover.getExpiresAt().isAfter(now())) {
            release(new Context(item, reservation, null, null), "EXPIRED");
        }
    }

    private Context lockContext(long actorId, long id) {
        Reservation seed = reservations.selectById(id);
        if (seed == null) throw notFound();
        Item seedItem = existingItem(items.selectById(seed.getItemId()));
        if (actorId != seed.getRequesterId() && actorId != seedItem.getOwnerId()) throw forbidden();
        // Universal lock order: participant users (ascending id), item, reservation, handover.
        Map<Long, UserAccount> people = lockPeople(seed.getRequesterId(), seedItem.getOwnerId());
        active(people.get(actorId));
        Item item = existingItem(items.lockById(seed.getItemId()));
        Reservation reservation = reservations.lockById(id);
        return new Context(item, reservation, people.get(item.getOwnerId()), people.get(reservation.getRequesterId()));
    }

    private Map<Long, UserAccount> lockPeople(long first, long second) {
        Map<Long, UserAccount> result = new HashMap<>();
        for (long id : new TreeSet<>(List.of(first, second))) result.put(id, users.lockById(id));
        return result;
    }

    private HandoverRecord acceptedHandover(Context context) {
        if (!"ACCEPTED".equals(context.reservation().getStatus()) || !"RESERVED".equals(context.item().getStatus())
                || Boolean.TRUE.equals(context.item().getDeleted()) || !"APPROVED".equals(context.item().getReviewStatus())) {
            throw conflict("当前预约不能交接或取消");
        }
        HandoverRecord handover = handovers.lockForReservation(context.reservation().getId());
        if (handover == null) throw conflict("交接凭证不存在");
        return handover;
    }

    private void ensureNotExpired(Context context, HandoverRecord handover) {
        if (!handover.getExpiresAt().isAfter(now()) || handover.getAttempts() >= maxAttempts) {
            release(context, "EXPIRED");
            throw new CommittedReservationException("预约或交接码已失效，物品已释放");
        }
    }

    private void expireForItem(Item item) {
        if (!"RESERVED".equals(item.getStatus())) return;
        Reservation reservation = reservations.lockAccepted(item.getId());
        if (reservation == null) return;
        HandoverRecord handover = handovers.lockForReservation(reservation.getId());
        if (handover != null && !handover.getExpiresAt().isAfter(now())) {
            release(new Context(item, reservation, null, null), "EXPIRED");
            item.setStatus("AVAILABLE");
        }
    }

    private void release(Context context, String status) {
        if (items.transition(context.item().getId(), "RESERVED", "AVAILABLE") != 1) throw conflict("物品状态已发生变化");
        transition(context.reservation(), status);
    }

    private void transition(Reservation reservation, String status) {
        if (reservations.transition(reservation.getId(), reservation.getStatus(), status, now()) != 1) throw conflict("预约状态已发生变化");
        reservation.setStatus(status);
        log.info("Reservation {} transitioned to {}", reservation.getId(), status);
    }

    private void owner(long actorId, Context context) { if (context.item().getOwnerId() != actorId) throw forbidden(); }
    private void active(UserAccount user) {
        if (user == null || !"ACTIVE".equals(user.getStatus())) throw new BusinessException(HttpStatus.FORBIDDEN, "预约参与者账号不可用");
    }
    private Item existingItem(Item item) { if (item == null) throw notFound(); return item; }
    // Existing MySQL DATETIME columns have second precision; return exactly what is persisted.
    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS); }
    private String newCode() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    private BusinessException notFound() { return new BusinessException(HttpStatus.NOT_FOUND, "物品或预约不存在"); }
    private BusinessException forbidden() { return new BusinessException(HttpStatus.FORBIDDEN, "无权操作此预约"); }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }
    private record Context(Item item, Reservation reservation, UserAccount owner, UserAccount requester) {}
}
