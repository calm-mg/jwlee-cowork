package io.autocrypt.jwlee.cowork.advancedslidesagent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * SlideGenerationRequest DTO as defined in DSL-AdvancedSlidesAgent.md.
 */
public record SlideGenerationRequest(
    @NotBlank(message = "Workspace ID cannot be empty")
    String workspaceId,
    @NotBlank(message = "Source material cannot be empty")
    String sourceMaterial,
    @NotBlank(message = "Instructions cannot be empty")
    String instructions
) {}
