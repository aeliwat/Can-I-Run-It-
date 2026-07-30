package com.canirunit.cli;

import com.canirunit.model.AiModel;
import com.canirunit.model.CompatibilityReport;
import com.canirunit.model.CompatibilityStatus;
import com.canirunit.model.Hardware;
import com.canirunit.model.ModelResult;
import com.canirunit.model.UpgradeSuggestion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a {@link CompatibilityReport} as ASCII table or JSON for the CLI.
 */
public final class CliReporter {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void printAscii(CompatibilityReport report) {
        System.out.println();
        System.out.println("==============================================");
        System.out.println("   Can I Run It?  —  Local AI Compatibility");
        System.out.println("==============================================");
        System.out.println();

        printHardware(report.hardware());
        System.out.println();
        printModelTable(report.results());
        System.out.println();
        printSuggestions(report.results());
        System.out.println();
        printLegend();
        System.out.println();
    }

    public void printJson(CompatibilityReport report) {
        System.out.println(gson.toJson(toDto(report)));
    }

    public String toJson(CompatibilityReport report) {
        return gson.toJson(toDto(report));
    }

    Map<String, Object> toDto(CompatibilityReport report) {
        Hardware hardware = report.hardware();

        List<Map<String, Object>> gpus = hardware.gpus().stream()
                .map(gpu -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("name", gpu.name());
                    map.put("vramGb", gpu.vramGb());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> hardwareDto = new LinkedHashMap<>();
        hardwareDto.put("totalRamGb", hardware.totalRamGb());
        hardwareDto.put("totalVramGb", hardware.totalVramGb());
        hardwareDto.put("gpus", gpus);

        List<Map<String, Object>> results = report.results().stream()
                .map(result -> {
                    AiModel model = result.model();
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("name", model.name());
                    map.put("category", model.category());
                    map.put("parametersInBillions", model.parametersInBillions());
                    map.put("quantizationBits", model.quantizationBits());
                    map.put("contextBufferGb", model.contextBufferGb());
                    map.put("custom", model.custom());
                    map.put("requiredMemoryGb", result.requiredMemoryGb());
                    map.put("status", result.status().name());
                    map.put("suggestion", suggestionDto(result.suggestion()));
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("hardware", hardwareDto);
        dto.put("results", results);
        return dto;
    }

    private static Map<String, Object> suggestionDto(UpgradeSuggestion suggestion) {
        if (suggestion == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("neededVramGb", suggestion.neededVramGb());
        map.put("currentVramGb", suggestion.currentVramGb());
        map.put("shortfallGb", suggestion.shortfallGb());
        map.put("summary", suggestion.summary());
        map.put("gpuExample", suggestion.gpuExample());
        return map;
    }

    private static void printHardware(Hardware hardware) {
        System.out.println("Detected Hardware");
        System.out.println("-----------------");
        System.out.printf(Locale.ROOT, "  System RAM : %.1f GB%n", hardware.totalRamGb());

        if (hardware.gpus().isEmpty()) {
            System.out.println("  GPU(s)     : none detected");
        } else {
            System.out.println("  GPU(s)     :");
            for (Hardware.GpuInfo gpu : hardware.gpus()) {
                System.out.printf(Locale.ROOT, "    - %s  (%.1f GB VRAM)%n", gpu.name(), gpu.vramGb());
            }
            System.out.printf(Locale.ROOT, "  Total VRAM : %.1f GB%n", hardware.totalVramGb());
        }
    }

    private static void printModelTable(List<ModelResult> results) {
        String header = String.format(
                Locale.ROOT,
                "| %-28s | %8s | %6s | %10s | %-14s |",
                "Model", "Params", "Quant", "Required", "Status"
        );
        String divider = "+-" + "-".repeat(28) + "-+-" + "-".repeat(8) + "-+-"
                + "-".repeat(6) + "-+-" + "-".repeat(10) + "-+-" + "-".repeat(14) + "-+";

        System.out.println("Model Compatibility");
        System.out.println("-------------------");
        System.out.println(divider);
        System.out.println(header);
        System.out.println(divider);

        for (ModelResult result : results) {
            AiModel model = result.model();
            String displayName = model.custom() ? model.name() + " *" : model.name();
            System.out.printf(
                    Locale.ROOT,
                    "| %-28s | %6.1fB | %4d-bit | %8.1f GB | %-14s |%n",
                    truncate(displayName, 28),
                    model.parametersInBillions(),
                    model.quantizationBits(),
                    result.requiredMemoryGb(),
                    result.status().name()
            );
        }

        System.out.println(divider);
    }

    private static void printSuggestions(List<ModelResult> results) {
        List<ModelResult> actionable = results.stream()
                .filter(r -> r.status() != CompatibilityStatus.OPTIMAL)
                .toList();

        System.out.println("How to make it OPTIMAL (GPU-fast)");
        System.out.println("--------------------------------");
        if (actionable.isEmpty()) {
            System.out.println("  All listed models are already OPTIMAL on this machine.");
            return;
        }

        for (ModelResult result : actionable) {
            UpgradeSuggestion tip = result.suggestion();
            System.out.printf(Locale.ROOT, "  • %s%n", result.model().name());
            System.out.printf(Locale.ROOT, "      %s%n", tip.summary());
        }
    }

    private static void printLegend() {
        System.out.println("Legend");
        System.out.println("------");
        System.out.println("  OPTIMAL       GPU VRAM > required memory");
        System.out.println("  SLOW          System RAM > required (CPU / offload)");
        System.out.println("  INCOMPATIBLE  RAM + VRAM < required memory");
        System.out.println();
        System.out.println("Formula: Required_GB = (params_B * quant_bits / 8) * 1.15 + context_buffer_GB");
        System.out.println("  * = custom model (saved in ~/.can-i-run-it/custom-models.json)");
        System.out.println("Tip:     java -jar can-i-run-it.jar --ui   opens the local web UI");
        System.out.println("         java -jar can-i-run-it.jar --add-model --name \"My LLM\" --params 8 --bits 4");
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
