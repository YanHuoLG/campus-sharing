package cn.edu.campus.sharing.user;

import cn.edu.campus.sharing.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserMapper users;

    public UserService(UserMapper users) { this.users = users; }

    public UserAccount requireActive(long id) {
        UserAccount account = users.selectById(id);
        if (account == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被封禁");
        }
        return account;
    }

    public UserResponse me(long id) { return UserResponse.from(requireActive(id)); }

    @Transactional
    public UserResponse update(long id, UpdateProfileRequest request) {
        if (request.nickname() == null && request.contactInfo() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请至少提供昵称或联系方式");
        }
        // Lock prevents concurrent partial updates from overwriting each other.
        UserAccount account = users.lockById(id);
        if (account == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已失效");
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被封禁");
        }
        String nickname = request.nickname() == null ? account.getNickname() : request.nickname().strip();
        String contact = request.contactInfo() == null ? account.getContactInfo() : request.contactInfo().strip();
        if (users.updateProfile(id, nickname, contact) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "账号状态已发生变化");
        }
        return me(id);
    }
}
