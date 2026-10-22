package edu.mec.studentportal.client.dto;

import java.time.LocalDateTime;

/** Create/update body for events; the client never sends status, creator or review metadata. */
public record EventRequest(
    String title,
    String description,
    String venue,
    LocalDateTime startTime,
    LocalDateTime endTime,
    int capacity
) {}
