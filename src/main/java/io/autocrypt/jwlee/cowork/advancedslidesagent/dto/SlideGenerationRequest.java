package io.autocrypt.jwlee.cowork.advancedslidesagent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * SlideGenerationRequest DTO as defined in DSL-AdvancedSlidesAgent.md.
 */
public record SlideGenerationRequest(
    @NotBlank(message = "Workspace ID cannot be empty")
    String workspaceId,
    String sourceString,
    String sourceFile,
    @NotBlank(message = "Instructions cannot be empty")
    String instructions
) {}
