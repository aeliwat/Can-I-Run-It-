package com.canirunit.model;

/**
 * One model's estimated requirement, compatibility verdict, and upgrade tip.
 */
public record ModelResult(
        AiModel model,
        double requiredMemoryGb,
        CompatibilityStatus status,
        UpgradeSuggestion suggestion
) {
}
