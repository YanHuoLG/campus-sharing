package cn.edu.campus.sharing.item;

import java.time.LocalDateTime;
import java.util.List;

public record ItemResponse(Long id, Long ownerId, String ownerNickname, Long categoryId,
                           String categoryName, String title, String description, String contactInfo,
                           String toolCondition, String reviewStatus, String reviewReason, String status,
                           List<ImageResponse> images, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public record ImageResponse(Long id, String url, Integer sortOrder) {}
}
