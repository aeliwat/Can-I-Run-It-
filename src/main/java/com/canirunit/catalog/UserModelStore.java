package com.canirunit.catalog;

import com.canirunit.model.AiModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persists user-added models under {@code ~/.can-i-run-it/custom-models.json}.
 */
public final class UserModelStore {

    private static final Type LIST_TYPE = new TypeToken<List<AiModel>>() {
    }.getType();

    private final Path storePath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public UserModelStore() {
        this(defaultStorePath());
    }

    public UserModelStore(Path storePath) {
        this.storePath = storePath;
    }

    public static Path defaultStorePath() {
        return Path.of(System.getProperty("user.home"), ".can-i-run-it", "custom-models.json");
    }

    public Path path() {
        return storePath;
    }

    public List<AiModel> loadAll() {
        if (!Files.isRegularFile(storePath)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(storePath, StandardCharsets.UTF_8)) {
            List<AiModel> models = gson.fromJson(reader, LIST_TYPE);
            if (models == null || models.isEmpty()) {
                return List.of();
            }
            return List.copyOf(models);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read custom models from " + storePath, e);
        }
    }

    /**
     * Add or replace a custom model (matched by name, case-insensitive).
     */
    public AiModel add(AiModel model) {
        AiModel validated = validate(model);
        List<AiModel> models = new ArrayList<>(loadAll());
        models.removeIf(existing -> namesEqual(existing.name(), validated.name()));
        models.add(asCustom(validated));
        save(models);
        return asCustom(validated);
    }

    /**
     * Remove a custom model by name. Returns true if something was removed.
     */
    public boolean remove(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Model name is required");
        }
        List<AiModel> models = new ArrayList<>(loadAll());
        boolean removed = models.removeIf(existing -> namesEqual(existing.name(), name));
        if (removed) {
            save(models);
        }
        return removed;
    }

    public Optional<AiModel> find(String name) {
        return loadAll().stream()
                .filter(model -> namesEqual(model.name(), name))
                .findFirst();
    }

    private void save(List<AiModel> models) {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(storePath, StandardCharsets.UTF_8)) {
                gson.toJson(models, writer);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write custom models to " + storePath, e);
        }
    }

    static AiModel validate(AiModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model is required");
        }
        String name = model.name() == null ? "" : model.name().trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Model name is required");
        }
        if (model.parametersInBillions() <= 0) {
            throw new IllegalArgumentException("parametersInBillions must be > 0");
        }
        if (model.quantizationBits() <= 0) {
            throw new IllegalArgumentException("quantizationBits must be > 0");
        }
        if (model.contextBufferGb() < 0) {
            throw new IllegalArgumentException("contextBufferGb must be >= 0");
        }
        String category = model.category() == null || model.category().isBlank()
                ? "LLM"
                : model.category().trim();
        return new AiModel(
                name,
                model.parametersInBillions(),
                model.quantizationBits(),
                model.contextBufferGb(),
                category,
                true
        );
    }

    private static AiModel asCustom(AiModel model) {
        return new AiModel(
                model.name(),
                model.parametersInBillions(),
                model.quantizationBits(),
                model.contextBufferGb(),
                model.category(),
                true
        );
    }

    private static boolean namesEqual(String a, String b) {
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
