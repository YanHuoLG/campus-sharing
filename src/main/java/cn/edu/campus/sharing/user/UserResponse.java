package cn.edu.campus.sharing.user;

import java.time.LocalDateTime;

public record UserResponse(Long id, String phone, String nickname, String contactInfo,
                           String role, String status, LocalDateTime createdAt) {
    public static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getPhone(), account.getNickname(),
                account.getContactInfo(), account.getRole(), account.getStatus(), account.getCreatedAt());
    }
}
