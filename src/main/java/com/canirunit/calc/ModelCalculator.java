package com.canirunit.calc;

import com.canirunit.model.AiModel;
import com.canirunit.model.CompatibilityStatus;
import com.canirunit.model.Hardware;
import com.canirunit.model.ModelResult;

/**
 * Estimates how much memory a model needs and how it fits on the host hardware.
 */
public class ModelCalculator {

    /**
     * Runtime overhead factor on top of raw weight size (framework buffers, fragmentation).
     */
    public static final double DEFAULT_OVERHEAD = 1.15;

    private final double overheadFactor;

    public ModelCalculator() {
        this(DEFAULT_OVERHEAD);
    }

    public ModelCalculator(double overheadFactor) {
        if (overheadFactor < 1.0) {
            throw new IllegalArgumentException("overheadFactor must be >= 1.0");
        }
        this.overheadFactor = overheadFactor;
    }

    /**
     * Rough weight memory in GB (with overhead), plus a fixed context / workspace buffer.
     *
     * <pre>
     *   Required_Memory_GB =
     *     ((parametersInBillions * quantizationBits) / 8) * overhead
     *     + contextBufferGb
     * </pre>
     *
     * Why it works: {@code parametersInBillions * quantizationBits} is the total number of
     * bits storing weights. Dividing by 8 converts bits → bytes; because the left side is
     * already in "billions of parameters", the result is already in gigabytes
     * (1e9 bytes ≈ 1 GB for this rule-of-thumb).
     *
     * Example: Llama 3 8B at 4-bit → (8 * 4) / 8 = 4 GB weights × 1.15 ≈ 4.6 GB + context.
     */
    public double calculateRequiredMemoryGb(AiModel model) {
        double weightMemoryGb = (model.parametersInBillions() * model.quantizationBits()) / 8.0;
        return (weightMemoryGb * overheadFactor) + model.contextBufferGb();
    }

    /**
     * Compare model requirements against detected RAM / VRAM.
     *
     * <ul>
     *   <li>OPTIMAL — GPU VRAM alone exceeds required memory</li>
     *   <li>SLOW — system RAM can hold it, but VRAM cannot (CPU offloading)</li>
     *   <li>INCOMPATIBLE — RAM + VRAM together are still too small</li>
     * </ul>
     */
    public CompatibilityStatus evaluateCompatibility(Hardware hardware, AiModel model) {
        double required = calculateRequiredMemoryGb(model);
        double vram = hardware.totalVramGb();
        double ram = hardware.totalRamGb();

        if (vram > required) {
            return CompatibilityStatus.OPTIMAL;
        }
        if (ram > required) {
            return CompatibilityStatus.SLOW;
        }
        if (ram + vram < required) {
            return CompatibilityStatus.INCOMPATIBLE;
        }

        // Edge case: VRAM <= required and RAM <= required, but RAM + VRAM >= required.
        // Treat as SLOW (partial offload / unified memory style fit).
        return CompatibilityStatus.SLOW;
    }

    public ModelResult evaluate(Hardware hardware, AiModel model) {
        double required = round1(calculateRequiredMemoryGb(model));
        CompatibilityStatus status = evaluateCompatibility(hardware, model);
        return new ModelResult(
                model,
                required,
                status,
                SuggestionEngine.suggest(hardware, required, status)
        );
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
