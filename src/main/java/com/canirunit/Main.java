package com.canirunit;

import com.canirunit.cli.CliReporter;
import com.canirunit.model.AiModel;
import com.canirunit.model.CompatibilityReport;
import com.canirunit.service.CompatibilityService;
import com.canirunit.ui.LocalWebUi;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Entry point: detect hardware, evaluate the model catalog, print a report or open the local UI.
 *
 * <pre>
 *   java -jar can-i-run-it.jar
 *   java -jar can-i-run-it.jar --json
 *   java -jar can-i-run-it.jar --ui
 *   java -jar can-i-run-it.jar --add-model --name "My LLM" --params 8 --bits 4 --buffer 1.0
 *   java -jar can-i-run-it.jar --remove-model "My LLM"
 * </pre>
 */
public class Main {

    private static final int DEFAULT_UI_PORT = 7421;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        boolean json = false;
        boolean ui = false;
        boolean noBrowser = false;
        boolean addModel = false;
        String removeModelName = null;
        int port = DEFAULT_UI_PORT;
        String bindAddress = envOrDefault("CAN_I_RUN_IT_BIND", "127.0.0.1");
        Path modelsFile = null;

        String modelName = null;
        Double params = null;
        Integer bits = null;
        Double buffer = null;
        String category = "LLM";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--json", "-j" -> json = true;
                case "--ui", "-u" -> ui = true;
                case "--no-browser" -> noBrowser = true;
                case "--add-model" -> addModel = true;
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                case "--port", "-p" -> port = parsePort(requireValue(args, ++i, "--port"));
                case "--bind" -> bindAddress = requireValue(args, ++i, "--bind");
                case "--models", "-m" -> modelsFile = Path.of(requireValue(args, ++i, "--models"));
                case "--remove-model" -> removeModelName = requireValue(args, ++i, "--remove-model");
                case "--name" -> modelName = requireValue(args, ++i, "--name");
                case "--params" -> params = Double.parseDouble(requireValue(args, ++i, "--params"));
                case "--bits" -> bits = Integer.parseInt(requireValue(args, ++i, "--bits"));
                case "--buffer" -> buffer = Double.parseDouble(requireValue(args, ++i, "--buffer"));
                case "--category" -> category = requireValue(args, ++i, "--category");
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        CompatibilityService service = new CompatibilityService();
        CliReporter reporter = new CliReporter();

        if (addModel) {
            if (json || ui || removeModelName != null) {
                throw new IllegalArgumentException("--add-model cannot be combined with --json, --ui, or --remove-model");
            }
            if (modelName == null || params == null || bits == null) {
                throw new IllegalArgumentException(
                        "--add-model requires --name, --params, and --bits (optional: --buffer, --category)"
                );
            }
            double contextBuffer = buffer == null ? 1.0 : buffer;
            AiModel added = service.addCustomModel(
                    new AiModel(modelName, params, bits, contextBuffer, category, true)
            );
            System.out.printf(
                    Locale.ROOT,
                    "Added custom model \"%s\" (%.1fB, %d-bit, %.1f GB buffer)%nSaved to %s%n",
                    added.name(),
                    added.parametersInBillions(),
                    added.quantizationBits(),
                    added.contextBufferGb(),
                    service.userStore().path()
            );
            return;
        }

        if (removeModelName != null) {
            if (json || ui) {
                throw new IllegalArgumentException("--remove-model cannot be combined with --json or --ui");
            }
            boolean removed = service.removeCustomModel(removeModelName);
            if (!removed) {
                throw new IllegalArgumentException("No custom model named \"" + removeModelName + "\"");
            }
            System.out.println("Removed custom model \"" + removeModelName + "\"");
            return;
        }

        if (json && ui) {
            throw new IllegalArgumentException("Choose either --json or --ui, not both");
        }

        if (ui) {
            new LocalWebUi(service, reporter, modelsFile).start(bindAddress, port, !noBrowser);
            Thread.currentThread().join();
            return;
        }

        CompatibilityReport report = modelsFile == null
                ? service.run()
                : service.run(modelsFile);

        if (json) {
            reporter.printJson(report);
        } else {
            reporter.printAscii(report);
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index];
    }

    private static int parsePort(String value) {
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return port;
    }

    private static void printUsage() {
        System.out.println("Can I Run It? — Local AI compatibility checker");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar can-i-run-it.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  (default)              Print an ASCII compatibility table");
        System.out.println("  --json, -j             Print JSON instead of a table");
        System.out.println("  --ui, -u               Start the local web UI (http://127.0.0.1:7421)");
        System.out.println("  --port, -p <n>         UI listen port (default: 7421)");
        System.out.println("  --bind <addr>          UI bind address (default: 127.0.0.1; use 0.0.0.0 in Docker)");
        System.out.println("  --no-browser           With --ui, do not open a browser");
        System.out.println("  --models, -m <f>       Load catalog from a JSON file (no custom merge)");
        System.out.println("  --add-model            Add a custom LLM (requires --name --params --bits)");
        System.out.println("  --name <text>          Model display name");
        System.out.println("  --params <n>           Parameters in billions (e.g. 8)");
        System.out.println("  --bits <n>             Quantization bits (e.g. 4)");
        System.out.println("  --buffer <n>           Context buffer GB (default: 1.0)");
        System.out.println("  --category <text>      Category (default: LLM)");
        System.out.println("  --remove-model <name>  Remove a previously added custom model");
        System.out.println("  --help, -h             Show this help");
        System.out.println();
        System.out.println("Custom models are saved to ~/.can-i-run-it/custom-models.json");
        System.out.println("Env:     CAN_I_RUN_IT_BIND overrides default bind address");
        System.out.printf(Locale.ROOT, "Default UI port: %d%n", DEFAULT_UI_PORT);
    }
}
