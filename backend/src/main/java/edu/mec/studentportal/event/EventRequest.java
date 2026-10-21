package edu.mec.studentportal.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Event create/update body per docs/API_CONTRACT.md. Server-controlled fields (status,
 * creator, review metadata, counts, timestamps) are never accepted from the client.
 */
public record EventRequest(
    @NotBlank @Size(max = 160) String title,
    @NotBlank String description,
    @NotBlank @Size(max = 160) String venue,
    @NotNull LocalDateTime startTime,
    @NotNull LocalDateTime endTime,
    @Positive int capacity
) {}
