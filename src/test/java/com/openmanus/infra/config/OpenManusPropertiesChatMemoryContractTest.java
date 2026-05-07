package com.openmanus.infra.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenManusPropertiesChatMemoryContractTest {

    @Test
    void chatMemoryConfig_removesModelContextSettingsAndKeepsToolResultBudget() {
        Class<?> configClass = OpenManusProperties.ChatMemoryConfig.class;
        List<String> fieldNames = Arrays.stream(configClass.getDeclaredFields())
                .map(Field::getName)
                .toList();
        List<String> methodNames = Arrays.stream(configClass.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(fieldNames).doesNotContain(
                "modelContextMaxMessages",
                "modelContextMaxTotalMessages",
                "modelContextMaxApproxTokens",
                "modelContextTokenCountMode"
        );
        assertThat(methodNames).doesNotContain(
                "getModelContextMaxMessages",
                "getModelContextMaxTotalMessages",
                "getModelContextMaxApproxTokens",
                "getModelContextTokenCountMode",
                "getModelContextTokenCountModeRaw",
                "setModelContextMaxMessages",
                "setModelContextMaxTotalMessages",
                "setModelContextMaxApproxTokens",
                "setModelContextTokenCountMode"
        );
        assertThat(methodNames).contains(
                "isToolResultBudgetEnabled",
                "getToolResultBudgetMinChars",
                "getToolResultBudgetPreviewHeadChars",
                "getToolResultBudgetPreviewTailChars",
                "getToolResultBudgetDecayChars"
        );
    }
}
