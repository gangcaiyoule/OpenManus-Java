package com.openmanus.aiframework.runtime;

/**
 * Runtime memory capability for idempotent system-message insertion.
 */
public interface AiSystemMessageMemory {

    /**
     * Ensures a system message exists exactly once in this memory.
     *
     * @param systemText system message text
     * @return true when inserted; false when already present
     */
    boolean ensureSystemMessage(String systemText);
}
