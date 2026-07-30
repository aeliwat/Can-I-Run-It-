package com.canirunit.ui;

import com.canirunit.cli.CliReporter;
import com.canirunit.model.AiModel;
import com.canirunit.model.CompatibilityReport;
import com.canirunit.service.CompatibilityService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Tiny local HTTP server that serves the static UI and live JSON APIs.
 */
public final class LocalWebUi {

    private static final String UI_ROOT = "/ui/";
    private static final Gson GSON = new Gson();

    private final CompatibilityService service;
    private final CliReporter reporter;
    private final Path modelsFile; // nullable → bundled + custom catalog

    public LocalWebUi(CompatibilityService service, CliReporter reporter, Path modelsFile) {
        this.service = service;
        this.reporter = reporter;
        this.modelsFile = modelsFile;
    }

    public void start(int port, boolean openBrowser) throws IOException {
        start("127.0.0.1", port, openBrowser);
    }

    public void start(String bindAddress, int port, boolean openBrowser) throws IOException {
        String host = (bindAddress == null || bindAddress.isBlank()) ? "127.0.0.1" : bindAddress.trim();
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/api/report", this::handleReport);
        server.createContext("/api/models", this::handleModels);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        String displayHost = "0.0.0.0".equals(host) || "::".equals(host) ? "127.0.0.1" : host;
        String url = "http://" + displayHost + ":" + port + "/";
        System.out.println();
        System.out.println("Can I Run It? UI is running at " + url);
        if ("0.0.0.0".equals(host)) {
            System.out.println("Listening on all interfaces (port " + port + ").");
        }
        System.out.println("Press Ctrl+C to stop.");
        System.out.println();

        if (openBrowser) {
            openInBrowser(url);
        }
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/index.html";
        }

        String resourcePath = UI_ROOT + path.replaceFirst("^/", "");
        if (resourcePath.contains("..") || !resourcePath.startsWith(UI_ROOT)) {
            sendText(exchange, 404, "text/plain; charset=utf-8", "Not Found");
            return;
        }

        try (InputStream in = LocalWebUi.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                sendText(exchange, 404, "text/plain; charset=utf-8", "Not Found");
                return;
            }
            byte[] body = in.readAllBytes();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(resourcePath));
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJsonError(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            CompatibilityReport report = modelsFile == null
                    ? service.run()
                    : service.run(modelsFile);
            sendText(exchange, 200, "application/json; charset=utf-8", reporter.toJson(report));
        } catch (Exception e) {
            sendJsonError(exchange, 500, messageOf(e));
        }
    }

    private void handleModels(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Methods", "POST, DELETE, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if ("POST".equalsIgnoreCase(method)) {
            handleAddModel(exchange);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            handleRemoveModel(exchange);
            return;
        }
        sendJsonError(exchange, 405, "Method Not Allowed");
    }

    private void handleAddModel(HttpExchange exchange) throws IOException {
        if (modelsFile != null) {
            sendJsonError(exchange, 400, "Cannot add models while using --models override");
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            String name = text(json, "name");
            double params = number(json, "parametersInBillions");
            int bits = (int) number(json, "quantizationBits");
            double buffer = json.has("contextBufferGb") ? number(json, "contextBufferGb") : 1.0;
            String category = json.has("category") && !json.get("category").isJsonNull()
                    ? json.get("category").getAsString()
                    : "LLM";

            AiModel added = service.addCustomModel(
                    new AiModel(name, params, bits, buffer, category, true)
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", true);
            response.put("model", modelDto(added));
            response.put("storePath", service.userStore().path().toString());
            sendText(exchange, 201, "application/json; charset=utf-8", GSON.toJson(response));
        } catch (IllegalArgumentException e) {
            sendJsonError(exchange, 400, messageOf(e));
        } catch (Exception e) {
            sendJsonError(exchange, 500, messageOf(e));
        }
    }

    private void handleRemoveModel(HttpExchange exchange) throws IOException {
        if (modelsFile != null) {
            sendJsonError(exchange, 400, "Cannot remove models while using --models override");
            return;
        }
        try {
            String name = queryParam(exchange.getRequestURI(), "name");
            if (name == null || name.isBlank()) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    name = text(json, "name");
                }
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            boolean removed = service.removeCustomModel(name);
            if (!removed) {
                sendJsonError(exchange, 404, "No custom model named \"" + name + "\"");
                return;
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", true);
            response.put("removed", name);
            sendText(exchange, 200, "application/json; charset=utf-8", GSON.toJson(response));
        } catch (IllegalArgumentException e) {
            sendJsonError(exchange, 400, messageOf(e));
        } catch (Exception e) {
            sendJsonError(exchange, 500, messageOf(e));
        }
    }

    private static Map<String, Object> modelDto(AiModel model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", model.name());
        map.put("parametersInBillions", model.parametersInBillions());
        map.put("quantizationBits", model.quantizationBits());
        map.put("contextBufferGb", model.contextBufferGb());
        map.put("category", model.category());
        map.put("custom", model.custom());
        return map;
    }

    private static String text(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String value = json.get(field).getAsString().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static double number(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return json.get(field).getAsDouble();
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private static String queryParam(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void sendJsonError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        sendText(exchange, status, "application/json; charset=utf-8", GSON.toJson(body));
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static void sendText(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // fall through to xdg-open
        }
        try {
            new ProcessBuilder("xdg-open", url).start();
        } catch (Exception e) {
            System.out.println("Open this URL in your browser: " + url);
        }
    }
}
