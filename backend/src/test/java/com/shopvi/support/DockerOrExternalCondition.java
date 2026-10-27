package com.shopvi.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/** Bật integration test khi có Docker hoặc khi chạy với {@code -Dit.external=true}. */
public class DockerOrExternalCondition implements ExecutionCondition {

    public static boolean external() {
        return Boolean.getBoolean("it.external");
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (external()) {
            return ConditionEvaluationResult.enabled("Using external infrastructure (it.external=true)");
        }
        if (DockerClientFactory.instance().isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker available");
        }
        return ConditionEvaluationResult.disabled("Docker not available and it.external not set");
    }
}
