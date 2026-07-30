package com.canirunit.model;

/**
 * Actionable tip for making a model run GPU-fast (OPTIMAL) on this machine.
 */
public record UpgradeSuggestion(
        double neededVramGb,
        double currentVramGb,
        double shortfallGb,
        String summary,
        String gpuExample
) {
}
