package com.canirunit.calc;

import com.canirunit.model.CompatibilityStatus;
import com.canirunit.model.Hardware;
import com.canirunit.model.UpgradeSuggestion;

import java.util.Locale;

/**
 * Builds human-readable upgrade tips so users know what VRAM they need for OPTIMAL (GPU-fast).
 */
public final class SuggestionEngine {

    /** Common consumer / pro GPU VRAM sizes (GB). */
    private static final double[] VRAM_TIERS = {8, 12, 16, 24, 32, 48, 80};

    private SuggestionEngine() {
    }

    public static UpgradeSuggestion suggest(
            Hardware hardware,
            double requiredMemoryGb,
            CompatibilityStatus status
    ) {
        double currentVram = round1(hardware.totalVramGb());
        double neededVram = recommendVramGb(requiredMemoryGb);
        double shortfall = round1(Math.max(0, neededVram - currentVram));
        String example = gpuExampleFor(neededVram);

        if (status == CompatibilityStatus.OPTIMAL) {
            return new UpgradeSuggestion(
                    neededVram,
                    currentVram,
                    0,
                    "Already optimal — your GPU VRAM covers this model.",
                    null
            );
        }

        if (status == CompatibilityStatus.SLOW) {
            String summary = String.format(
                    Locale.ROOT,
                    "Runs on RAM (slow). For OPTIMAL / GPU-fast, need ~%.0f GB VRAM (you have %.1f GB; short +%.1f GB). Example: %s.",
                    neededVram,
                    currentVram,
                    shortfall,
                    example
            );
            return new UpgradeSuggestion(neededVram, currentVram, shortfall, summary, example);
        }

        // INCOMPATIBLE
        String summary = String.format(
                Locale.ROOT,
                "Not enough RAM+VRAM to load. For OPTIMAL / GPU-fast, need ~%.0f GB VRAM (you have %.1f GB; short +%.1f GB). Example: %s.",
                neededVram,
                currentVram,
                shortfall,
                example
        );
        return new UpgradeSuggestion(neededVram, currentVram, shortfall, summary, example);
    }

    /**
     * Smallest common VRAM tier that is strictly greater than the required memory
     * (matches the OPTIMAL rule: VRAM &gt; required).
     */
    static double recommendVramGb(double requiredMemoryGb) {
        for (double tier : VRAM_TIERS) {
            if (tier > requiredMemoryGb) {
                return tier;
            }
        }
        // Beyond listed tiers — round up to next 16 GB block above required.
        return Math.ceil((requiredMemoryGb + 0.1) / 16.0) * 16.0;
    }

    static String gpuExampleFor(double neededVramGb) {
        if (neededVramGb <= 8) {
            return "RTX 4060 8GB";
        }
        if (neededVramGb <= 12) {
            return "RTX 3060 12GB";
        }
        if (neededVramGb <= 16) {
            return "RTX 4070 Ti / 4080 16GB";
        }
        if (neededVramGb <= 24) {
            return "RTX 4090 / 3090 24GB";
        }
        if (neededVramGb <= 32) {
            return "high-end 32GB card or dual GPUs";
        }
        if (neededVramGb <= 48) {
            return "RTX A6000 48GB";
        }
        if (neededVramGb <= 80) {
            return "A100 / H100 80GB";
        }
        return "multi-GPU / datacenter setup";
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
