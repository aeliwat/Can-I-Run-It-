package com.canirunit.catalog;

import com.canirunit.model.AiModel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Loads the AI model database from JSON and merges user-added custom models.
 */
public final class ModelCatalog {

    private static final String CLASSPATH_RESOURCE = "/models.json";
    private static final Type LIST_TYPE = new TypeToken<List<AiModel>>() {
    }.getType();

    private final Gson gson = new Gson();
    private final UserModelStore userStore;

    public ModelCatalog() {
        this(new UserModelStore());
    }

    public ModelCatalog(UserModelStore userStore) {
        this.userStore = Objects.requireNonNull(userStore, "userStore");
    }

    public UserModelStore userStore() {
        return userStore;
    }

    /**
     * Bundled catalog plus any user-added models from {@link UserModelStore}.
     * Custom models with the same name replace bundled entries.
     */
    public List<AiModel> loadEffective() {
        return merge(loadBundled(), userStore.loadAll());
    }

    /**
     * Load only the bundled {@code models.json} on the classpath.
     */
    public List<AiModel> loadBundled() {
        try (InputStream in = ModelCatalog.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Bundled models.json not found on classpath");
            }
            return read(new InputStreamReader(in, StandardCharsets.UTF_8), false);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read bundled models.json", e);
        }
    }

    /**
     * Load models from an explicit JSON file path (full override — no custom merge).
     */
    public List<AiModel> loadFromFile(Path path) {
        Objects.requireNonNull(path, "path");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader, false);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read models from " + path, e);
        }
    }

    private List<AiModel> read(Reader reader, boolean custom) {
        List<AiModel> models = gson.fromJson(reader, LIST_TYPE);
        if (models == null || models.isEmpty()) {
            throw new IllegalStateException("Model catalog is empty");
        }
        List<AiModel> normalized = new ArrayList<>(models.size());
        for (AiModel model : models) {
            normalized.add(new AiModel(
                    model.name(),
                    model.parametersInBillions(),
                    model.quantizationBits(),
                    model.contextBufferGb(),
                    model.category(),
                    custom || model.custom()
            ));
        }
        return List.copyOf(normalized);
    }

    static List<AiModel> merge(List<AiModel> bundled, List<AiModel> custom) {
        Map<String, AiModel> byName = new LinkedHashMap<>();
        for (AiModel model : bundled) {
            byName.put(key(model.name()), model);
        }
        for (AiModel model : custom) {
            byName.put(key(model.name()), new AiModel(
                    model.name(),
                    model.parametersInBillions(),
                    model.quantizationBits(),
                    model.contextBufferGb(),
                    model.category(),
                    true
            ));
        }
        return List.copyOf(byName.values());
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
