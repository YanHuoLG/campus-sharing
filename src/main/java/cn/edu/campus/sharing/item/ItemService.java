package cn.edu.campus.sharing.item;

import cn.edu.campus.sharing.category.*;
import cn.edu.campus.sharing.common.*;
import cn.edu.campus.sharing.user.*;
import cn.edu.campus.sharing.reservation.ReservationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ItemService {
    private final ItemMapper items;
    private final ItemImageMapper images;
    private final UserMapper users;
    private final UserService userService;
    private final CategoryMapper categories;
    private final ImageStorage storage;
    private final ReservationMapper reservations;

    public ItemService(ItemMapper items, ItemImageMapper images, UserMapper users,
                       UserService userService, CategoryMapper categories, ImageStorage storage, ReservationMapper reservations) {
        this.items = items;
        this.images = images;
        this.users = users;
        this.userService = userService;
        this.categories = categories;
        this.storage = storage;
        this.reservations = reservations;
    }

    @Transactional
    public ItemResponse create(long actorId, ItemRequest request, List<MultipartFile> files) {
        UserAccount actor = lockActive(actorId, false);
        validateCategory(request);
        List<String> paths = storage.store(files);
        Item item = new Item();
        item.setOwnerId(actorId);
        apply(item, request);
        item.setStatus("AVAILABLE");
        item.setDeleted(false);
        item.setCreatedAt(LocalDateTime.now());
        items.insert(item);
        insertImages(item.getId(), paths);
        return response(item, actor);
    }

    @Transactional
    public ItemResponse update(long actorId, long id, ItemRequest request) {
        UserAccount actor = lockActive(actorId, false);
        Item item = ownedAvailable(actorId, id);
        validateCategory(request);
        apply(item, request);
        items.updateById(item);
        reservations.closePending(id, "CANCELLED");
        return response(item, actor);
    }

    @Transactional
    public ItemResponse replaceImages(long actorId, long id, List<MultipartFile> files) {
        UserAccount actor = lockActive(actorId, false);
        Item item = ownedAvailable(actorId, id);
        List<String> paths = storage.store(files);
        List<String> previous = imageRows(id).stream().map(ItemImage::getFilePath).toList();
        images.delete(new LambdaQueryWrapper<ItemImage>().eq(ItemImage::getItemId, id));
        insertImages(id, paths);
        item.setReviewStatus("PENDING");
        item.setReviewReason(null);
        item.setUpdatedAt(LocalDateTime.now());
        items.updateById(item);
        storage.deleteAfterCommit(previous);
        reservations.closePending(id, "CANCELLED");
        return response(item, actor);
    }

    @Transactional
    public void remove(long actorId, long id, boolean asAdmin) {
        lockActive(actorId, asAdmin);
        Item item = asAdmin ? existing(items.lockById(id)) : ownedAvailable(actorId, id);
        reservations.cancelActive(id);
        if (asAdmin && "RESERVED".equals(item.getStatus())) item.setStatus("AVAILABLE");
        item.setDeleted(true);
        item.setUpdatedAt(LocalDateTime.now());
        items.updateById(item);
        if (asAdmin) items.audit(actorId, "DELETE_ITEM", id);
    }

    @Transactional
    public ItemResponse review(long adminId, long id, String reason, boolean approve) {
        UserAccount admin = lockActive(adminId, true);
        Item item = existing(items.lockById(id));
        if (!"PENDING".equals(item.getReviewStatus()) || !"AVAILABLE".equals(item.getStatus())) {
            throw conflict("只有待审核且未被占用的物品可以审核");
        }
        if (approve) {
            userService.requireActive(item.getOwnerId());
            int count = imageRows(id).size();
            if (count < 1 || count > 2) throw conflict("物品必须有1至2张图片才能通过审核");
        }
        item.setReviewStatus(approve ? "APPROVED" : "REJECTED");
        item.setReviewReason(approve ? null : reason.strip());
        item.setUpdatedAt(LocalDateTime.now());
        items.updateById(item);
        items.audit(adminId, approve ? "APPROVE_ITEM" : "REJECT_ITEM", id);
        return response(item, admin);
    }

    @Transactional(readOnly = true)
    public ItemResponse detail(long actorId, long id) {
        UserAccount actor = userService.requireActive(actorId);
        Item item = visible(actor, id);
        return response(item, actor);
    }

    @Transactional(readOnly = true)
    public ImageContent image(long actorId, long id, long imageId) {
        UserAccount actor = userService.requireActive(actorId);
        visible(actor, id);
        ItemImage image = images.selectById(imageId);
        if (image == null || !Objects.equals(image.getItemId(), id)) throw notFound();
        return new ImageContent(storage.read(image.getFilePath()),
                image.getFilePath().endsWith(".png") ? "image/png" : "image/jpeg");
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> list(long actorId, int page, int size, Long categoryId,
            String keyword, String status, String reviewStatus, boolean mine, boolean asAdmin) {
        UserAccount actor = userService.requireActive(actorId);
        if (asAdmin && !"ADMIN".equals(actor.getRole())) throw forbidden();
        if (categoryId != null && categories.selectById(categoryId) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "物品分类不存在");
        }
        boolean publicOnly = !mine && !asAdmin;
        String pattern = "%" + keyword.strip().replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        ItemQuery query = new ItemQuery(mine ? actorId : null, categoryId,
                publicOnly ? "APPROVED" : reviewStatus, status, publicOnly, pattern, size, ((long) page - 1) * size);
        List<Item> found = items.search(query);
        return new PageResponse<>(responses(found, actor), page, size, items.countSearch(query));
    }

    private UserAccount lockActive(long id, boolean adminRequired) {
        UserAccount actor = users.lockById(id);
        if (actor == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已失效");
        if (!"ACTIVE".equals(actor.getStatus())) throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被封禁");
        if (adminRequired && !"ADMIN".equals(actor.getRole())) throw forbidden();
        return actor;
    }

    private Item ownedAvailable(long actorId, long id) {
        Item item = existing(items.lockById(id));
        if (!Objects.equals(item.getOwnerId(), actorId)) throw forbidden();
        if (!"AVAILABLE".equals(item.getStatus())) throw conflict("已被预约或完成交接的物品不能修改或下架");
        return item;
    }

    private Item visible(UserAccount actor, long id) {
        Item item = existing(items.selectById(id));
        if (Objects.equals(item.getOwnerId(), actor.getId()) || "ADMIN".equals(actor.getRole())) return item;
        UserAccount owner = users.selectById(item.getOwnerId());
        if (!"APPROVED".equals(item.getReviewStatus()) || owner == null || !"ACTIVE".equals(owner.getStatus())) {
            throw notFound();
        }
        return item;
    }

    private Item existing(Item item) {
        if (item == null || Boolean.TRUE.equals(item.getDeleted())) throw notFound();
        return item;
    }

    private void validateCategory(ItemRequest request) {
        if (categories.selectById(request.categoryId()) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "物品分类不存在");
        }
        if (request.categoryId() == 3L && request.toolCondition() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "专业工具必须填写精度状态");
        }
        if (request.categoryId() != 3L && request.toolCondition() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅专业工具可以填写精度状态");
        }
    }

    private void apply(Item item, ItemRequest request) {
        item.setCategoryId(request.categoryId());
        item.setTitle(request.title().strip());
        item.setDescription(request.description().strip());
        item.setContactInfo(request.contactInfo().strip());
        item.setToolCondition(request.toolCondition());
        item.setReviewStatus("PENDING");
        item.setReviewReason(null);
        item.setUpdatedAt(LocalDateTime.now());
    }

    private void insertImages(long id, List<String> paths) {
        for (int i = 0; i < paths.size(); i++) {
            ItemImage row = new ItemImage();
            row.setItemId(id);
            row.setFilePath(paths.get(i));
            row.setSortOrder(i + 1);
            images.insert(row);
        }
    }

    private List<ItemImage> imageRows(long id) {
        return images.selectList(new LambdaQueryWrapper<ItemImage>().eq(ItemImage::getItemId, id)
                .orderByAsc(ItemImage::getSortOrder));
    }

    private ItemResponse response(Item item, UserAccount actor) { return responses(List.of(item), actor).getFirst(); }

    private List<ItemResponse> responses(List<Item> found, UserAccount actor) {
        if (found.isEmpty()) return List.of();
        Set<Long> ownerIds = found.stream().map(Item::getOwnerId).collect(Collectors.toSet());
        Set<Long> categoryIds = found.stream().map(Item::getCategoryId).collect(Collectors.toSet());
        Map<Long, UserAccount> owners = users.selectByIds(ownerIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        Map<Long, Category> categoryMap = categories.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, List<ItemImage>> grouped = images.selectList(new LambdaQueryWrapper<ItemImage>()
                .in(ItemImage::getItemId, found.stream().map(Item::getId).toList())
                .orderByAsc(ItemImage::getSortOrder)).stream().collect(Collectors.groupingBy(ItemImage::getItemId));
        return found.stream().map(item -> new ItemResponse(item.getId(), item.getOwnerId(),
                owners.get(item.getOwnerId()).getNickname(), item.getCategoryId(), categoryMap.get(item.getCategoryId()).getName(),
                item.getTitle(), item.getDescription(), item.getContactInfo(), item.getToolCondition(), item.getReviewStatus(),
                Objects.equals(actor.getId(), item.getOwnerId()) || "ADMIN".equals(actor.getRole()) ? item.getReviewReason() : null,
                item.getStatus(), grouped.getOrDefault(item.getId(), List.of()).stream()
                    .map(image -> new ItemResponse.ImageResponse(image.getId(), "/api/items/" + item.getId() + "/images/" + image.getId(), image.getSortOrder()))
                    .toList(), item.getCreatedAt(), item.getUpdatedAt())).toList();
    }

    private BusinessException notFound() { return new BusinessException(HttpStatus.NOT_FOUND, "物品或图片不存在"); }
    private BusinessException forbidden() { return new BusinessException(HttpStatus.FORBIDDEN, "无权操作此物品"); }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }

    public record ImageContent(byte[] bytes, String contentType) {}
}
