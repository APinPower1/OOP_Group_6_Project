package edu.mec.studentportal.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rejection body per docs/API_CONTRACT.md; a non-blank reason is mandatory. */
public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {}
