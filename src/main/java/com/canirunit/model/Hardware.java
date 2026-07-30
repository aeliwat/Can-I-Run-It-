package com.canirunit.model;

import java.util.List;

/**
 * Snapshot of the machine's memory resources used for compatibility checks.
 */
public record Hardware(
        double totalRamGb,
        List<GpuInfo> gpus
) {
    /**
     * Sum of VRAM across all detected GPUs (GB).
     * Integrated / unknown devices may report 0 until nvidia-smi fallback fills it in.
     */
    public double totalVramGb() {
        return gpus.stream().mapToDouble(GpuInfo::vramGb).sum();
    }

    public record GpuInfo(String name, double vramGb) {
    }
}
