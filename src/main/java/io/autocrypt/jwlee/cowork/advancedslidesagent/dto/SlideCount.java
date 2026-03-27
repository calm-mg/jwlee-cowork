package io.autocrypt.jwlee.cowork.advancedslidesagent.dto;

import jakarta.validation.constraints.Min;

/**
 * SlideCount DTO for structured parsing of slide counts.
 */
public record SlideCount(
    @Min(1)
    int count
) {}
