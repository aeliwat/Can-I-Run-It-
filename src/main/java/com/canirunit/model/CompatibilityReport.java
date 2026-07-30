package com.canirunit.model;

import java.util.List;

/**
 * Full snapshot: detected hardware plus evaluated model results.
 */
public record CompatibilityReport(
        Hardware hardware,
        List<ModelResult> results
) {
}
