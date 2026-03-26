package io.autocrypt.jwlee.cowork.advancedslidesagent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * SlideMarkdownOutput DTO as defined in DSL-AdvancedSlidesAgent.md.
 */
public record SlideMarkdownOutput(
    @NotBlank(message = "Markdown content cannot be empty")
    String markdownContent,
    @NotBlank(message = "Saved file path cannot be empty")
    String savedFilePath
) {}
