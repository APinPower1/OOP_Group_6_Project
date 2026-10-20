package edu.mec.studentportal.club;

import java.time.LocalDateTime;

/** Club details response including calculated active-member count. */
public record ClubResponse(
    long id,
    String name,
    String description,
    String category,
    boolean active,
    int activeMemberCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    static ClubResponse from(Club club, int activeMemberCount) {
        return new ClubResponse(
            club.id(),
            club.name(),
            club.description(),
            club.category(),
            club.active(),
            activeMemberCount,
            club.createdAt(),
            club.updatedAt()
        );
    }
}
