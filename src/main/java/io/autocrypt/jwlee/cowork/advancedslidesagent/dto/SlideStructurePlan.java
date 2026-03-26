package io.autocrypt.jwlee.cowork.advancedslidesagent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * SlideStructurePlan DTO as defined in DSL-AdvancedSlidesAgent.md.
 */
public record SlideStructurePlan(
    @Min(value = 1, message = "Slide count must be at least 1")
    int slideCount,
    @NotBlank(message = "Outline cannot be empty")
    String outline
) {}
