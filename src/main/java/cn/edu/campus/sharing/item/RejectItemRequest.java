package cn.edu.campus.sharing.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectItemRequest(@NotBlank @Size(max = 500) String reason) {}
