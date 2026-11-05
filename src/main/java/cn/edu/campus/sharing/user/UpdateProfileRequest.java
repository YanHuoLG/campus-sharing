package cn.edu.campus.sharing.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 40) @Pattern(regexp = "(?sU).*\\S.*", message = "不能只包含空白字符") String nickname,
        @Size(max = 200) @Pattern(regexp = "(?sU).*\\S.*", message = "不能只包含空白字符") String contactInfo) {}
