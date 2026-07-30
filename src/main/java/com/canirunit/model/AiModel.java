package com.canirunit.model;

/**
 * Describes a local AI model and the knobs that drive its memory footprint.
 *
 * <ul>
 *   <li>{@code parametersInBillions} — model size (e.g. 8.0 for an 8B model)</li>
 *   <li>{@code quantizationBits} — bits per parameter after quantization (4, 8, 16, …)</li>
 *   <li>{@code contextBufferGb} — extra RAM reserved for KV-cache / activations / workspace</li>
 *   <li>{@code category} — coarse type for UI grouping (LLM, Image, Audio, …)</li>
 *   <li>{@code custom} — true when the user added this model (persisted under ~/.can-i-run-it)</li>
 * </ul>
 */
public record AiModel(
        String name,
        double parametersInBillions,
        int quantizationBits,
        double contextBufferGb,
        String category,
        boolean custom
) {
    public AiModel {
        if (category == null || category.isBlank()) {
            category = "LLM";
        }
    }

    /** Convenience for bundled / non-custom models. */
    public AiModel(
            String name,
            double parametersInBillions,
            int quantizationBits,
            double contextBufferGb,
            String category
    ) {
        this(name, parametersInBillions, quantizationBits, contextBufferGb, category, false);
    }
}
