package com.canirunit.service;

import com.canirunit.calc.ModelCalculator;
import com.canirunit.catalog.ModelCatalog;
import com.canirunit.catalog.UserModelStore;
import com.canirunit.detect.HardwareDetector;
import com.canirunit.model.AiModel;
import com.canirunit.model.CompatibilityReport;
import com.canirunit.model.Hardware;
import com.canirunit.model.ModelResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates hardware detection + model catalog evaluation into a single report.
 */
public class CompatibilityService {

    private final HardwareDetector detector;
    private final ModelCalculator calculator;
    private final ModelCatalog catalog;

    public CompatibilityService() {
        this(new HardwareDetector(), new ModelCalculator(), new ModelCatalog());
    }

    public CompatibilityService(
            HardwareDetector detector,
            ModelCalculator calculator,
            ModelCatalog catalog
    ) {
        this.detector = detector;
        this.calculator = calculator;
        this.catalog = catalog;
    }

    public ModelCatalog catalog() {
        return catalog;
    }

    public UserModelStore userStore() {
        return catalog.userStore();
    }

    public CompatibilityReport run() {
        return run(catalog.loadEffective());
    }

    public CompatibilityReport run(Path modelsFile) {
        return run(catalog.loadFromFile(modelsFile));
    }

    public CompatibilityReport run(List<AiModel> models) {
        Hardware hardware = detector.detect();
        List<ModelResult> results = new ArrayList<>(models.size());
        for (AiModel model : models) {
            results.add(calculator.evaluate(hardware, model));
        }
        return new CompatibilityReport(hardware, List.copyOf(results));
    }

    public AiModel addCustomModel(AiModel model) {
        return userStore().add(model);
    }

    public boolean removeCustomModel(String name) {
        return userStore().remove(name);
    }
}
