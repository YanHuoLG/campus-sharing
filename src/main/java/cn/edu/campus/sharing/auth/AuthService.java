package cn.edu.campus.sharing.auth;

import cn.edu.campus.sharing.common.BusinessException;
import cn.edu.campus.sharing.user.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public AuthService(UserMapper users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        validatePasswordBytes(request.password());
        UserAccount account = new UserAccount();
        account.setPhone(request.phone());
        account.setPasswordHash(passwords.encode(request.password()));
        account.setNickname(request.nickname().strip());
        account.setContactInfo(request.contactInfo().strip());
        account.setRole("STUDENT");
        account.setStatus("ACTIVE");
        try {
            users.insert(account);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "该手机号已注册");
        }
        return UserResponse.from(users.selectById(account.getId()));
    }

    public UserResponse login(LoginRequest request) {
        validatePasswordBytes(request.password());
        UserAccount account = users.findByPhone(request.phone());
        boolean matches = passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被封禁");
        }
        return UserResponse.from(account);
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "密码的 UTF-8 编码长度不能超过72字节");
        }
    }
}
