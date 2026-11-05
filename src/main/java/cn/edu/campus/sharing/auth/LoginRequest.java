package cn.edu.campus.sharing.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}", message = "请输入有效的11位手机号") String phone,
        @NotBlank @Size(min = 8, max = 64) String password) {}
