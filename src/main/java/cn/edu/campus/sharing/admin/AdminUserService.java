package cn.edu.campus.sharing.admin;

import cn.edu.campus.sharing.common.BusinessException;
import cn.edu.campus.sharing.common.PageResponse;
import cn.edu.campus.sharing.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {
    private final UserMapper users;

    public AdminUserService(UserMapper users) { this.users = users; }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size, String keyword) {
        String pattern = "%" + keyword.strip().replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        return new PageResponse<>(users.search(pattern, size, ((long) page - 1) * size).stream()
                .map(UserResponse::from).toList(), page, size, users.countSearch(pattern));
    }

    @Transactional
    public UserResponse setBanned(long adminId, long id, boolean banned) {
        // Locks and state update share one transaction with the audit log.
        // Same ascending user lock order as reservation operations.
        UserAccount first = users.lockById(Math.min(adminId, id));
        UserAccount second = adminId == id ? first : users.lockById(Math.max(adminId, id));
        UserAccount admin = adminId <= id ? first : second;
        if (admin == null || !"ACTIVE".equals(admin.getStatus()) || !"ADMIN".equals(admin.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权执行此操作");
        }
        if (adminId == id) throw new BusinessException(HttpStatus.CONFLICT, "不能封禁或解封自己");
        UserAccount account = adminId <= id ? second : first;
        if (account == null) throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        if (!"STUDENT".equals(account.getRole())) {
            throw new BusinessException(HttpStatus.CONFLICT, "此接口仅管理学生账号");
        }
        String status = banned ? "BANNED" : "ACTIVE";
        if (!status.equals(account.getStatus())) {
            users.setStatus(id, status);
            users.audit(adminId, banned ? "BAN_USER" : "UNBAN_USER", id);
            account.setStatus(status);
        }
        return UserResponse.from(account);
    }
}
