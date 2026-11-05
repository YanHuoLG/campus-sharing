package cn.edu.campus.sharing.item;

public record ItemQuery(Long ownerId, Long categoryId, String reviewStatus, String status,
                        boolean publicOnly, String pattern, int size, long offset) {}
