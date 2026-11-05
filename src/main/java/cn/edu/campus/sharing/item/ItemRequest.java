package cn.edu.campus.sharing.item;

import jakarta.validation.constraints.*;

public record ItemRequest(
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 200) String contactInfo,
        @Pattern(regexp = "NORMAL|MINOR_ERROR|UNUSABLE", message = "精度状态须为 NORMAL、MINOR_ERROR 或 UNUSABLE") String toolCondition) {}
