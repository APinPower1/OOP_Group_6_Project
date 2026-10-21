package edu.mec.studentportal.user;

import java.util.List;

public record AdminUserPageResponse(
    List<AdminUserResponse> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
    public AdminUserPageResponse {
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems must not be negative");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must not be negative");
        }
        items = List.copyOf(items);
    }
}
