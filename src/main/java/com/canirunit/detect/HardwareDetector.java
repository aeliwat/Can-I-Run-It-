package com.canirunit.detect;

import com.canirunit.model.Hardware;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detects system RAM and GPUs via OSHI, with an nvidia-smi fallback for VRAM.
 */
public class HardwareDetector {

    private static final double BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0;

    /**
     * Probe the local machine and return a {@link Hardware} snapshot.
     */
    public Hardware detect() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();

        // OSHI reports physical memory in bytes; convert to gigabytes for the UI / math.
        double totalRamGb = hal.getMemory().getTotal() / BYTES_PER_GB;

        List<Hardware.GpuInfo> gpus = new ArrayList<>();
        List<GraphicsCard> cards = hal.getGraphicsCards();

        if (cards == null || cards.isEmpty()) {
            // No GPU enumerated — still try nvidia-smi in case the driver is present.
            double nvidiaVram = queryNvidiaSmiVramGb();
            if (nvidiaVram > 0) {
                gpus.add(new Hardware.GpuInfo("NVIDIA GPU (via nvidia-smi)", nvidiaVram));
            }
        } else {
            for (GraphicsCard card : cards) {
                String name = card.getName() != null ? card.getName() : "Unknown GPU";
                // OSHI VRAM is in bytes; some NVIDIA setups incorrectly report 0.
                double vramGb = card.getVRam() / BYTES_PER_GB;

                if (vramGb <= 0 && looksLikeNvidia(name)) {
                    double fallback = queryNvidiaSmiVramGb();
                    if (fallback > 0) {
                        vramGb = fallback;
                    }
                }

                gpus.add(new Hardware.GpuInfo(name, round1(vramGb)));
            }
        }

        return new Hardware(round1(totalRamGb), List.copyOf(gpus));
    }

    /**
     * Fallback when OSHI reports 0 VRAM for a dedicated NVIDIA card.
     * Runs: nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits
     * Output is MiB; we convert MiB → GiB by dividing by 1024.
     */
    double queryNvidiaSmiVramGb() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "nvidia-smi",
                    "--query-gpu=memory.total",
                    "--format=csv,noheader,nounits"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            double totalMib = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    // Multi-GPU machines print one MiB value per line — sum them.
                    totalMib += Double.parseDouble(trimmed);
                }
            }

            int exit = process.waitFor();
            if (exit != 0 || totalMib <= 0) {
                return 0;
            }
            return round1(totalMib / 1024.0);
        } catch (Exception e) {
            // nvidia-smi missing, permission denied, parse failure, etc.
            return 0;
        }
    }

    private static boolean looksLikeNvidia(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("nvidia") || lower.contains("geforce") || lower.contains("quadro")
                || lower.contains("tesla") || lower.contains("rtx") || lower.contains("gtx");
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
