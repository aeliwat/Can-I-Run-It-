package com.canirunit.model;

/**
 * How comfortably a model fits on the detected hardware.
 */
public enum CompatibilityStatus {
    /** GPU VRAM alone covers the required memory — best case for local inference. */
    OPTIMAL,

    /**
     * System RAM can hold the model, but VRAM is insufficient.
     * Inference will rely on CPU (or CPU+GPU) offloading and will be slower.
     */
    SLOW,

    /** Combined RAM + VRAM still cannot fit the model. */
    INCOMPATIBLE
}
