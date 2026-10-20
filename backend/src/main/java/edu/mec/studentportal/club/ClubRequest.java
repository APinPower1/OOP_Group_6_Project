package edu.mec.studentportal.club;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Club create/update request; the client never sets id/active flags. */
public record ClubRequest(
    @NotBlank @Size(max = 140) String name,
    @NotBlank String description,
    @NotBlank @Size(max = 100) String category
) {}
