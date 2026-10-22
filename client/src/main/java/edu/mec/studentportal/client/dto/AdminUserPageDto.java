package edu.mec.studentportal.client.dto;

import java.util.List;

/** Paginated admin user list envelope (D-013); totalPages is 0 when empty. */
public record AdminUserPageDto(
    List<AdminUserDto> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
    public AdminUserPageDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
