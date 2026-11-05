package cn.edu.campus.sharing.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}", message = "请输入有效的11位手机号") String phone,
        @NotBlank @Size(min = 8, max = 64, message = "密码长度须为8至64个字符") String password,
        @NotBlank @Size(max = 40) String nickname,
        @NotBlank @Size(max = 200) String contactInfo) {}
