package io.autocrypt.jwlee.cowork.advancedslidesagent.dto;

import com.embabel.agent.api.annotation.State;
import io.autocrypt.jwlee.cowork.advancedslidesagent.AdvancedSlidesAgent.Stage;

/**
 * SlideGenerationState state as defined in DSL-AdvancedSlidesAgent.md.
 */
@State
public record SlideGenerationState(
    SlideGenerationRequest request,
    SlideStructurePlan plan
) implements Stage {}
