package com.openmanus.aiframework.runtime;

@FunctionalInterface
public interface AiMemoryProvider {

    AiMemory get(Object memoryId);
}
