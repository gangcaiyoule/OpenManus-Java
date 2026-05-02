package com.openmanus.agent.base;

import com.openmanus.agent.context.assembly.ContextAssembler;
import com.openmanus.agent.context.assembly.ContextBudgetPolicy;
import com.openmanus.agent.context.assembly.ContextSnapshot;
import com.openmanus.agent.context.assembly.TaskExecutionState;
import com.openmanus.agent.context.assembly.TaskExecutionStateTracker;
import com.openmanus.agent.context.compression.IndexedRehydrateSelector;
import com.openmanus.agent.context.token.ModelContextTokenCounter;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiSystemMessageMemory;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import com.openmanus.aiframework.runtime.ToolResultArtifactRef;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.AiToolExecutionRequest;
import com.openmanus.aiframework.tool.AiToolRegistry;
import com.openmanus.domain.service.ExecutionEventPort;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import static com.openmanus.aiframework.runtime.AiLogMarkers.TO_FRONTEND;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * An abstract base class for creating agent executors that operate in a ReAct (Reason-Act) loop.
 * This class manages the interaction between a language model, a set of tools, and the user's request.
 * It orchestrates the cycle of generating a response (thought), executing a tool if requested,
 * and feeding the result back into the model until a final answer is produced.
 *
 * @param <B> The type of the builder used to construct this executor, allowing for fluent chaining in subclasses.
 */
@Slf4j
public abstract class AbstractAgentExecutor<B extends AbstractAgentExecutor.Builder<B>> extends AbstractAgent<B> {

    /**
     * An abstract builder for configuring and creating instances of {@link AbstractAgentExecutor}.
     *
     * @param <B> The concrete builder type, enabling method chaining.
     */
    public static abstract class Builder<B extends AbstractAgentExecutor.Builder<B>> extends AbstractAgent.Builder<B> {

        AiChatModel aiChatModel;
        String systemMessage;
        AiMemoryProvider aiMemoryProvider;
        boolean enableToolResultCompaction = false;
        int memoryToolResultMaxChars = 4000;
        int compactToolResultHeadChars = 300;
        int compactToolResultTailChars = 200;
        int modelContextMaxMessages = 0;
        int modelContextMaxTotalMessages = 0;
        int modelContextMaxApproxTokens = 0;
        String modelContextTokenCountMode = ModelContextTokenCounter.MODE_APPROX;
        String modelContextTokenizerModel = "";
        int maxIterations = 0;
        int maxExecutionSeconds = 0;
        int repeatedToolCallThreshold = 0;
        int taskStatePlanMaxChars = TaskExecutionState.Budget.DEFAULT_PLAN_MAX_CHARS;
        int taskStateInProgressMaxChars = TaskExecutionState.Budget.DEFAULT_IN_PROGRESS_MAX_CHARS;
        int taskStateLastFailureMaxChars = TaskExecutionState.Budget.DEFAULT_LAST_FAILURE_MAX_CHARS;
        int taskStateTodoMaxItems = TaskExecutionState.Budget.DEFAULT_TODO_MAX_ITEMS;
        int taskStateTodoItemMaxChars = TaskExecutionState.Budget.DEFAULT_TODO_ITEM_MAX_CHARS;
        boolean enableToolResultOffload = false;
        int toolResultOffloadMinChars = 12000;
        int toolResultOffloadHeadChars = 240;
        int toolResultOffloadTailChars = 160;
        boolean enableToolResultRehydrate = false;
        int toolResultRehydrateMaxChars = 8000;
        int toolResultRehydrateMaxPerRound = 0;
        AiToolResultArtifactStore toolResultArtifactStore;
        ExecutionEventPort executionEventPort;
        final Map<String, AiRegisteredTool> tools = new HashMap<>();

        /**
         * Sets the execution event port for publishing progress events to WebSocket.
         */
        public B executionEventPort(ExecutionEventPort executionEventPort) {
            this.executionEventPort = executionEventPort;
            return result();
        }

        /**
         * Sets runtime-first chat model used by the agent loop.
         */
        public B aiChatModel(AiChatModel model) {
            this.aiChatModel = model;
            return result();
        }

        /**
         * Sets runtime-first chat memory provider used by the agent loop.
         */
        public B aiMemoryProvider(AiMemoryProvider aiMemoryProvider) {
            this.aiMemoryProvider = aiMemoryProvider;
            return result();
        }

        /**
         * Enables or disables long tool-result compaction before persisting into memory.
         * Disabled by default to preserve full message continuity across turns.
         */
        public B enableToolResultCompaction(boolean enabled) {
            this.enableToolResultCompaction = enabled;
            return result();
        }

        /**
         * Sets max chars for tool results persisted into chat memory.
         * Large outputs are compacted to reduce long-conversation context bloat.
         */
        public B memoryToolResultMaxChars(int maxChars) {
            this.memoryToolResultMaxChars = Math.max(256, maxChars);
            return result();
        }

        /**
         * Sets how many leading chars to keep when compacting large tool results in chat memory.
         */
        public B compactToolResultHeadChars(int headChars) {
            this.compactToolResultHeadChars = Math.max(64, headChars);
            return result();
        }

        /**
         * Sets how many trailing chars to keep when compacting large tool results in chat memory.
         */
        public B compactToolResultTailChars(int tailChars) {
            this.compactToolResultTailChars = Math.max(32, tailChars);
            return result();
        }

        /**
         * Limits how many historical messages are sent to the model each round.
         * 0 means unlimited (default).
         */
        public B modelContextMaxMessages(int maxMessages) {
            this.modelContextMaxMessages = Math.max(0, maxMessages);
            return result();
        }

        /**
         * Limits total messages sent to model each round (history + current-turn messages).
         * 0 means unlimited (default).
         */
        public B modelContextMaxTotalMessages(int maxMessages) {
            this.modelContextMaxTotalMessages = Math.max(0, maxMessages);
            return result();
        }

        /**
         * Limits approximate token budget for model input each round.
         * 0 means unlimited (default).
         */
        public B modelContextMaxApproxTokens(int maxApproxTokens) {
            this.modelContextMaxApproxTokens = Math.max(0, maxApproxTokens);
            return result();
        }

        /**
         * Sets token counting mode for context budgeting.
         * Supported values: approx | tokenizer.
         */
        public B modelContextTokenCountMode(String mode) {
            this.modelContextTokenCountMode = ModelContextTokenCounter.normalizeMode(mode);
            return result();
        }

        /**
         * Configured model name used by tokenizer mode encoding mapping.
         */
        public B modelContextTokenizerModel(String modelName) {
            this.modelContextTokenizerModel = modelName == null ? "" : modelName.trim();
            return result();
        }

        /**
         * Limits ReAct loop iterations for one execute.
         * 0 means unlimited (continue as long as model keeps producing tool calls).
         */
        public B maxIterations(int maxIterations) {
            this.maxIterations = Math.max(0, maxIterations);
            return result();
        }

        /**
         * Limits execution time for one execute in seconds.
         * 0 means unlimited.
         */
        public B maxExecutionSeconds(int maxExecutionSeconds) {
            this.maxExecutionSeconds = Math.max(0, maxExecutionSeconds);
            return result();
        }

        /**
         * Detects repeated identical tool-call batches and aborts after threshold.
         * 0 means disabled.
         */
        public B repeatedToolCallThreshold(int repeatedToolCallThreshold) {
            this.repeatedToolCallThreshold = Math.max(0, repeatedToolCallThreshold);
            return result();
        }

        public B taskStatePlanMaxChars(int maxChars) {
            this.taskStatePlanMaxChars = maxChars <= 0
                    ? TaskExecutionState.Budget.DEFAULT_PLAN_MAX_CHARS
                    : maxChars;
            return result();
        }

        public B taskStateInProgressMaxChars(int maxChars) {
            this.taskStateInProgressMaxChars = maxChars <= 0
                    ? TaskExecutionState.Budget.DEFAULT_IN_PROGRESS_MAX_CHARS
                    : maxChars;
            return result();
        }

        public B taskStateLastFailureMaxChars(int maxChars) {
            this.taskStateLastFailureMaxChars = maxChars <= 0
                    ? TaskExecutionState.Budget.DEFAULT_LAST_FAILURE_MAX_CHARS
                    : maxChars;
            return result();
        }

        public B taskStateTodoMaxItems(int maxItems) {
            this.taskStateTodoMaxItems = maxItems <= 0
                    ? TaskExecutionState.Budget.DEFAULT_TODO_MAX_ITEMS
                    : maxItems;
            return result();
        }

        public B taskStateTodoItemMaxChars(int maxChars) {
            this.taskStateTodoItemMaxChars = maxChars <= 0
                    ? TaskExecutionState.Budget.DEFAULT_TODO_ITEM_MAX_CHARS
                    : maxChars;
            return result();
        }

        /**
         * Enables lossless offloading for very large tool results.
         * When enabled, oversized outputs are persisted into artifact store and memory keeps a compact index card.
         */
        public B enableToolResultOffload(boolean enabled) {
            this.enableToolResultOffload = enabled;
            return result();
        }

        /**
         * Minimum chars to trigger lossless tool-result offloading.
         */
        public B toolResultOffloadMinChars(int minChars) {
            this.toolResultOffloadMinChars = Math.max(256, minChars);
            return result();
        }

        /**
         * Sets leading preview chars for offloaded tool-result index card.
         */
        public B toolResultOffloadHeadChars(int headChars) {
            this.toolResultOffloadHeadChars = Math.max(64, headChars);
            return result();
        }

        /**
         * Sets trailing preview chars for offloaded tool-result index card.
         */
        public B toolResultOffloadTailChars(int tailChars) {
            this.toolResultOffloadTailChars = Math.max(32, tailChars);
            return result();
        }

        /**
         * Enables rehydrating offloaded tool-result artifacts back into model input.
         */
        public B enableToolResultRehydrate(boolean enabled) {
            this.enableToolResultRehydrate = enabled;
            return result();
        }

        /**
         * Max chars allowed for each rehydrated tool result.
         */
        public B toolResultRehydrateMaxChars(int maxChars) {
            this.toolResultRehydrateMaxChars = Math.max(256, maxChars);
            return result();
        }

        /**
         * Max count of tool-result artifacts rehydrated per model round.
         * 0 means unlimited.
         */
        public B toolResultRehydrateMaxPerRound(int maxCount) {
            this.toolResultRehydrateMaxPerRound = Math.max(0, maxCount);
            return result();
        }

        /**
         * Artifact store used by tool-result offloading.
         */
        public B toolResultArtifactStore(AiToolResultArtifactStore store) {
            this.toolResultArtifactStore = store;
            return result();
        }

        /**
         * Adds a pre-configured tool to the agent.
         * @param tool A prebuilt tool registration.
         * @return The builder instance for chaining.
         */
        public B tool(AiRegisteredTool tool) {
            registerTool(tool);
            return result();
        }

        /**
         * Scans an object for methods annotated with {@link com.openmanus.aiframework.tool.AiTool}
         * and adds them as executable tools for the agent.
         *
         * @param objectWithTools The object containing tool methods.
         * @return The builder instance for chaining.
         */
        public B toolFromObject( Object objectWithTools ) {
            AiToolRegistry.scan(objectWithTools).forEach(this::registerTool);
            return result();
        }

        /**
         * A convenience method to add tools from multiple objects.
         *
         * @param objectsWithTools A varargs array of objects containing tool methods.
         * @return The builder instance for chaining.
         */
        public B toolsFromObjects( Object... objectsWithTools ) {
            for (Object tool : objectsWithTools) {
                toolFromObject(tool);
            }
            return result();
        }

        private void registerTool(AiRegisteredTool tool) {
            Objects.requireNonNull(tool, "tool cannot be null");
            AiRegisteredTool existing = tools.putIfAbsent(tool.name(), tool);
            if (existing != null) {
                throw new IllegalStateException("Duplicate tool name detected: " + tool.name());
            }
        }

        public B systemMessage(String message) {
            this.systemMessage = Objects.requireNonNull(message, "message cannot be null");
            return result();
        }
    }

    private final AiChatModel aiChatModel;
    private final String systemMessage;
    private final AiMemoryProvider aiMemoryProvider;
    private final boolean enableToolResultCompaction;
    private final int memoryToolResultMaxChars;
    private final int compactToolResultHeadChars;
    private final int compactToolResultTailChars;
    private final int modelContextMaxMessages;
    private final int modelContextMaxTotalMessages;
    private final int modelContextMaxApproxTokens;
    private final String modelContextTokenCountMode;
    private final String modelContextTokenizerModel;
    private final int maxIterations;
    private final int maxExecutionSeconds;
    private final int repeatedToolCallThreshold;
    private final TaskExecutionState.Budget taskStateBudget;
    private final boolean enableToolResultOffload;
    private final int toolResultOffloadMinChars;
    private final int toolResultOffloadHeadChars;
    private final int toolResultOffloadTailChars;
    private final boolean enableToolResultRehydrate;
    private final int toolResultRehydrateMaxChars;
    private final int toolResultRehydrateMaxPerRound;
    private final AiToolResultArtifactStore toolResultArtifactStore;
    private final ExecutionEventPort executionEventPort;
    private final ContextBudgetPolicy contextBudgetPolicy;
    private final ContextAssembler contextAssembler;
    private final Map<String, AiRegisteredTool> tools;
    private final List<AiToolSpec> toolSpecifications;
    private static final int SYSTEM_MESSAGE_LOCK_STRIPES = 1024;
    private static final int LOOP_CONTINUITY_MIN_PREVIEW_CHARS = 128;
    private static final int MAX_CONSECUTIVE_UNKNOWN_TOOL_CALLS = 3;
    private static final int INDEXED_REHYDRATE_FETCH_LIMIT_MIN = 8;
    private static final int INDEXED_REHYDRATE_FETCH_LIMIT_MAX = 128;
    private static final ReentrantLock[] SYSTEM_MESSAGE_LOCKS = createSystemMessageLocks();

    public AbstractAgentExecutor( Builder<B> builder ) {
        super( builder );
        this.aiChatModel = resolveAiChatModel(builder);
        this.systemMessage = builder.systemMessage;
        this.aiMemoryProvider = builder.aiMemoryProvider;
        this.enableToolResultCompaction = builder.enableToolResultCompaction;
        this.memoryToolResultMaxChars = builder.memoryToolResultMaxChars;
        this.compactToolResultHeadChars = builder.compactToolResultHeadChars;
        this.compactToolResultTailChars = builder.compactToolResultTailChars;
        this.modelContextMaxMessages = builder.modelContextMaxMessages;
        this.modelContextMaxTotalMessages = builder.modelContextMaxTotalMessages;
        this.modelContextMaxApproxTokens = builder.modelContextMaxApproxTokens;
        this.modelContextTokenCountMode = ModelContextTokenCounter.normalizeMode(
                builder.modelContextTokenCountMode);
        this.modelContextTokenizerModel = builder.modelContextTokenizerModel == null
                ? ""
                : builder.modelContextTokenizerModel.trim();
        this.maxIterations = builder.maxIterations;
        this.maxExecutionSeconds = builder.maxExecutionSeconds;
        this.repeatedToolCallThreshold = builder.repeatedToolCallThreshold;
        this.taskStateBudget = new TaskExecutionState.Budget(
                builder.taskStatePlanMaxChars,
                builder.taskStateInProgressMaxChars,
                builder.taskStateLastFailureMaxChars,
                builder.taskStateTodoMaxItems,
                builder.taskStateTodoItemMaxChars
        );
        this.enableToolResultOffload = builder.enableToolResultOffload;
        this.toolResultOffloadMinChars = builder.toolResultOffloadMinChars;
        this.toolResultOffloadHeadChars = builder.toolResultOffloadHeadChars;
        this.toolResultOffloadTailChars = builder.toolResultOffloadTailChars;
        this.enableToolResultRehydrate = builder.enableToolResultRehydrate;
        this.toolResultRehydrateMaxChars = builder.toolResultRehydrateMaxChars;
        this.toolResultRehydrateMaxPerRound = builder.toolResultRehydrateMaxPerRound;
        this.toolResultArtifactStore = builder.toolResultArtifactStore;
        this.executionEventPort = builder.executionEventPort;
        this.contextBudgetPolicy = new ContextBudgetPolicy(
                this.modelContextMaxMessages,
                this.modelContextMaxTotalMessages,
                this.modelContextMaxApproxTokens,
                ModelContextTokenCounter.create(
                        this.modelContextTokenCountMode,
                        this.modelContextTokenizerModel
                )
        );
        this.contextAssembler = new ContextAssembler(this.contextBudgetPolicy, this.taskStateBudget);
        validateToolResultStoreConfiguration();
        this.tools = Collections.unmodifiableMap(new HashMap<>(builder.tools));
        this.toolSpecifications = AiToolRegistry.toRuntimeToolSpecifications(this.tools.values());
    }

    private void validateToolResultStoreConfiguration() {
        if (enableToolResultOffload && toolResultArtifactStore == null) {
            throw new IllegalStateException(
                    "toolResultArtifactStore must be configured when toolResultOffload is enabled");
        }
        if (enableToolResultRehydrate && toolResultArtifactStore == null) {
            throw new IllegalStateException(
                    "toolResultArtifactStore must be configured when toolResultRehydrate is enabled");
        }
    }

    private AiChatModel resolveAiChatModel(Builder<B> builder) {
        if (builder.aiChatModel == null) {
            throw new IllegalStateException("aiChatModel must be configured");
        }
        return builder.aiChatModel;
    }

    /**
     * Executes the agent's ReAct loop to process a user request.
     * The method initiates a conversation with the language model, executes tools as directed by the model,
     * and continues this cycle until the model provides a final answer or the maximum number of iterations is reached.
     *
     * @param userInput The incoming user prompt.
     * @param memoryId A unique identifier for the conversation memory, allowing the agent to maintain context
     *                 across multiple turns (if the underlying tools support it).
     * @return The final text response from the agent.
     * @throws RuntimeException if the agent exceeds the maximum number of iterations or if the model fails to respond.
     */
    public String execute(String userInput, Object memoryId) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput cannot be null or blank");
        }
        log.info("Starting agent execution with input length: {}", userInput.length());
        log.info("MemoryId: {}", memoryId != null ? memoryId.toString() : "null");

        AiMemory memory = resolveMemory(memoryId);

        MessageListState messageList = initializeMessageList(memory);

        boolean hasSystemMessage = messageList.messages().stream()
                .anyMatch(message -> message != null && message.role() == AiChatMessage.Role.SYSTEM);
        if (!hasSystemMessage && systemMessage != null && !systemMessage.isBlank()) {
            AiChatMessage runtimeSystemMessage = AiChatMessage.system(systemMessage);
            messageList.addAtHead(runtimeSystemMessage);
            if (memory != null) {
                ensureSystemMessageInMemory(memory, memoryId, runtimeSystemMessage);
            }
        }
        log.info("Context prepared from chat memory: memoryId={}, messageCount={}", memoryId, messageList.size());

        AiChatMessage userMessage = extractUserMessageFromInput(userInput);
        messageList.appendAndPersist(userMessage, memory);

        long startNs = System.nanoTime();
        TaskExecutionState taskExecutionState = TaskExecutionState.empty(taskStateBudget);
        String lastToolBatchFingerprint = null;
        int repeatedToolBatchCount = 0;
        String lastUnknownToolFingerprint = null;
        int consecutiveUnknownToolBatchCount = 0;
        for (int i = 0; ; i++) {
            if (maxIterations > 0 && i >= maxIterations) {
                throw new RuntimeException("Agent exceeded maximum iterations (" + maxIterations + ")");
            }
            if (maxExecutionSeconds > 0) {
                long elapsedSeconds = (System.nanoTime() - startNs) / 1_000_000_000L;
                if (elapsedSeconds >= maxExecutionSeconds) {
                    throw new RuntimeException("Agent exceeded maximum execution seconds (" + maxExecutionSeconds + ")");
                }
            }
            log.info("Agent Iteration #{}", i + 1);

            publishIterationEvent(memoryId, i + 1, taskExecutionState);

            List<AiChatMessage> modelMessagesBeforeBudget = buildModelMessages(
                    messageList.messages(),
                    userMessage,
                    taskExecutionState
            );
            List<AiChatMessage> modelMessagesAfterFirstBudget = applyApproxTokenBudget(modelMessagesBeforeBudget, userMessage);
            List<AiChatMessage> modelMessagesAfterIndexedRehydrate =
                    maybeInjectIndexedRehydration(modelMessagesAfterFirstBudget, userMessage, memoryId);
            List<AiChatMessage> modelMessages = applyApproxTokenBudget(modelMessagesAfterIndexedRehydrate, userMessage);
            logContextGovernance(
                    i + 1,
                    modelMessagesBeforeBudget,
                    modelMessagesAfterFirstBudget,
                    modelMessagesAfterIndexedRehydrate,
                    modelMessages
            );
            AiChatRequest runtimeRequest = new AiChatRequest(
                    "",
                    modelMessages,
                    toolSpecifications,
                    null,
                    null,
                    null,
                    null
            );
            AiChatResponse runtimeResponse = aiChatModel.chat(runtimeRequest);
            if (runtimeResponse == null || runtimeResponse.message() == null) {
                throw new RuntimeException("LLM failed to generate a response.");
            }

            AiChatMessage assistantMessage = normalizeAssistantToolCallIds(runtimeResponse.message());
            messageList.appendAndPersist(assistantMessage, memory);
            taskExecutionState = TaskExecutionStateTracker.updateFromAssistantPlan(
                    taskExecutionState,
                    assistantMessage,
                    taskStateBudget
            );

            if (assistantMessage.toolCalls() == null || assistantMessage.toolCalls().isEmpty()) {
                log.info("Agent finished with a final answer.");
                return assistantMessage.content() == null ? "" : assistantMessage.content();
            }

            List<AiToolCall> requests = assistantMessage.toolCalls();
            List<AiToolCall> validRequests = requests.stream()
                    .filter(request -> request != null
                            && request.name() != null
                            && !request.name().isBlank())
                    .collect(Collectors.toList());
            if (validRequests.isEmpty()) {
                throw new RuntimeException("LLM returned tool calls without a valid tool name.");
            }
            String currentFingerprint = toolBatchFingerprint(validRequests);
            if (repeatedToolCallThreshold > 0) {
                if (Objects.equals(lastToolBatchFingerprint, currentFingerprint)) {
                    repeatedToolBatchCount++;
                } else {
                    repeatedToolBatchCount = 1;
                    lastToolBatchFingerprint = currentFingerprint;
                }
                if (repeatedToolBatchCount > repeatedToolCallThreshold) {
                    throw new RuntimeException("Agent aborted due to repeated identical tool-call batch (threshold="
                            + repeatedToolCallThreshold + ")");
                }
            }
            boolean unknownToolOnlyBatch = validRequests.stream()
                    .allMatch(request -> !tools.containsKey(request.name()));
            if (unknownToolOnlyBatch) {
                if (Objects.equals(lastUnknownToolFingerprint, currentFingerprint)) {
                    consecutiveUnknownToolBatchCount++;
                } else {
                    lastUnknownToolFingerprint = currentFingerprint;
                    consecutiveUnknownToolBatchCount = 1;
                }
                if (consecutiveUnknownToolBatchCount > MAX_CONSECUTIVE_UNKNOWN_TOOL_CALLS) {
                    throw new RuntimeException(
                            "Agent aborted due to repeated unknown tool-call batch (threshold="
                                    + MAX_CONSECUTIVE_UNKNOWN_TOOL_CALLS + ")");
                }
            } else {
                lastUnknownToolFingerprint = null;
                consecutiveUnknownToolBatchCount = 0;
            }

            for (AiToolCall request : validRequests) {
                String toolCallId = resolveToolCallId(request);
                AiToolExecutionRequest toolRequest =
                        new AiToolExecutionRequest(toolCallId, request.name(), request.arguments());
                taskExecutionState = TaskExecutionStateTracker.markToolStarted(
                        taskExecutionState,
                        request.name(),
                        taskStateBudget
                );
                log.debug("Executing tool: {}", request.name());
                log.info(TO_FRONTEND, "│  🔧 执行工具: {}", request.name());

                AiRegisteredTool toolEntry = tools.get(request.name());
                String outcome;
                if (toolEntry == null) {
                    outcome = "Tool not found: " + request.name();
                    taskExecutionState = TaskExecutionStateTracker.markToolFailed(
                            taskExecutionState,
                            request.name(),
                            outcome,
                            taskStateBudget
                    );
                } else {
                    try {
                        outcome = toolEntry.executor().execute(toolRequest, memoryId);
                        taskExecutionState = TaskExecutionStateTracker.markToolSucceeded(
                                taskExecutionState,
                                request.name(),
                                taskStateBudget
                        );
                    } catch (RuntimeException ex) {
                        taskExecutionState = TaskExecutionStateTracker.markToolFailed(
                                taskExecutionState,
                                request.name(),
                                ex.getMessage(),
                                taskStateBudget
                        );
                        throw ex;
                    }
                }

                AiChatMessage persistedToolResult = compactForMemoryIfNeeded(toolRequest, outcome, memoryId);
                AiChatMessage modelToolResult = modelToolResultForLoop(toolRequest, outcome, persistedToolResult);
                messageList.appendForModel(modelToolResult);
                persistToMemory(memory, persistedToolResult);

                log.info(TO_FRONTEND, "│  ✔️  工具执行完成: {}", request.name());
            }
        }
    }

    private AiChatMessage normalizeAssistantToolCallIds(AiChatMessage message) {
        if (message == null
                || message.role() != AiChatMessage.Role.ASSISTANT
                || message.toolCalls() == null
                || message.toolCalls().isEmpty()) {
            return message;
        }
        boolean changed = false;
        List<AiToolCall> normalized = new ArrayList<>(message.toolCalls().size());
        for (AiToolCall call : message.toolCalls()) {
            if (call == null) {
                normalized.add(null);
                continue;
            }
            if (call.id() == null || call.id().isBlank()) {
                normalized.add(call.withId(resolveToolCallId(call)));
                changed = true;
            } else {
                normalized.add(call);
            }
        }
        if (!changed) {
            return message;
        }
        return new AiChatMessage(
                AiChatMessage.Role.ASSISTANT,
                message.content(),
                message.name(),
                message.toolCallId(),
                normalized
        );
    }

    private AiMemory resolveMemory(Object memoryId) {
        if (aiMemoryProvider == null || memoryId == null) {
            return null;
        }
        AiMemory memory = aiMemoryProvider.get(memoryId);
        if (memory == null) {
            throw new IllegalStateException(
                    "aiMemoryProvider returned null for memoryId: " + memoryId);
        }
        return memory;
    }

    /**
     * Extracts the user's core message from runtime user input.
     * The default execution mode uses plain-text arguments.
     * For backward compatibility, only strict legacy shape {"context":"..."} is unpacked.
     */
    private AiChatMessage extractUserMessageFromInput(String userInput) {
        String arguments = userInput;
        if (arguments == null) {
            throw new IllegalArgumentException("userInput cannot be null or blank");
        }
        try {
            var jsonArgs = com.google.gson.JsonParser.parseString(arguments).getAsJsonObject();
            boolean strictLegacyShape = jsonArgs.size() == 1
                    && jsonArgs.has("context")
                    && jsonArgs.get("context").isJsonPrimitive()
                    && jsonArgs.get("context").getAsJsonPrimitive().isString();
            if (strictLegacyShape) {
                arguments = jsonArgs.get("context").getAsString();
            }
        } catch (Exception e) {
            // Plain-text arguments are the default in the current execution pipeline.
        }
        if (arguments.isBlank()) {
            throw new IllegalArgumentException("userInput cannot be null or blank");
        }
        return AiChatMessage.user(arguments);
    }

    private AiChatMessage compactForMemoryIfNeeded(AiToolExecutionRequest request,
                                                    String outcome,
                                                    Object memoryId) {
        AiChatMessage offloaded = offloadForMemoryIfNeeded(request, outcome, memoryId);
        if (offloaded != null) {
            return offloaded;
        }
        if (!enableToolResultCompaction || outcome == null || outcome.length() <= memoryToolResultMaxChars) {
            return toolMessage(request.id(), request.name(), outcome);
        }

        int head = Math.min(compactToolResultHeadChars, outcome.length());
        int tail = Math.min(compactToolResultTailChars, Math.max(0, outcome.length() - head));
        String headPart = outcome.substring(0, head);
        String tailPart = tail > 0 ? outcome.substring(outcome.length() - tail) : "";

        String compacted = """
                [Tool Result Compacted]
                tool=%s
                originalChars=%d
                sha256=%s
                previewHead:
                %s
                previewTail:
                %s
                """
                .formatted(request.name(), outcome.length(), sha256Hex(outcome), headPart, tailPart);

        return toolMessage(request.id(), request.name(), compacted);
    }

    private AiChatMessage modelToolResultForLoop(AiToolExecutionRequest request,
                                                  String outcome,
                                                  AiChatMessage persistedToolResult) {
        if (persistedToolResult == null) {
            return toolMessage(request.id(), request.name(), outcome);
        }
        String persistedText = persistedToolResult.content();
        if (persistedText == null) {
            return toolMessage(request.id(), request.name(), outcome);
        }
        if (persistedText.startsWith("[Tool Result Compacted]")) {
            return persistedToolResult;
        }
        if (persistedText.startsWith("[Tool Result Offloaded]")) {
            return buildLoopContinuityToolResult(request, outcome, persistedText);
        }
        return toolMessage(request.id(), request.name(), outcome);
    }

    private AiChatMessage buildLoopContinuityToolResult(AiToolExecutionRequest request,
                                                         String outcome,
                                                         String persistedText) {
        if (outcome == null) {
            return toolMessage(request.id(), request.name(), persistedText);
        }
        if (outcome.length() <= toolResultRehydrateMaxChars) {
            return toolMessage(request.id(), request.name(), outcome);
        }

        int previewBudget = Math.max(
                LOOP_CONTINUITY_MIN_PREVIEW_CHARS,
                Math.min(outcome.length(), Math.max(256, toolResultRehydrateMaxChars))
        );
        int head = Math.max(64, (previewBudget * 2) / 3);
        head = Math.min(head, outcome.length());
        int tail = Math.max(32, previewBudget - head);
        tail = Math.min(tail, Math.max(0, outcome.length() - head));
        String headPart = outcome.substring(0, head);
        String tailPart = tail > 0 ? outcome.substring(outcome.length() - tail) : "";
        String artifactId = extractArtifactId(persistedText);

        String continuity = """
                %s
                source=loop-continuity
                originalChars=%d
                sha256=%s
                artifactId=%s
                previewHead:
                %s
                previewTail:
                %s
                """
                .formatted(
                        persistedText.startsWith("[Tool Result Offloaded]")
                                ? "[Tool Result Offloaded]" : "[Tool Result Compacted]",
                        outcome.length(),
                        sha256Hex(outcome),
                        artifactId == null ? "n/a" : artifactId,
                        headPart,
                        tailPart
                );
        return toolMessage(request.id(), request.name(), continuity);
    }

    private AiChatMessage offloadForMemoryIfNeeded(AiToolExecutionRequest request,
                                                    String outcome,
                                                    Object memoryId) {
        if (!enableToolResultOffload || toolResultArtifactStore == null || outcome == null) {
            return null;
        }
        if (outcome.length() < toolResultOffloadMinChars) {
            return null;
        }
        try {
            String artifactId = toolResultArtifactStore.save(memoryId, request.name(), request.arguments(), outcome);
            int head = Math.min(toolResultOffloadHeadChars, outcome.length());
            int tail = Math.min(toolResultOffloadTailChars, Math.max(0, outcome.length() - head));
            String headPart = outcome.substring(0, head);
            String tailPart = tail > 0 ? outcome.substring(outcome.length() - tail) : "";

            String offloaded = """
                    [Tool Result Offloaded]
                    tool=%s
                    originalChars=%d
                    sha256=%s
                    artifactId=%s
                    previewHead:
                    %s
                    previewTail:
                    %s
                    """
                    .formatted(request.name(), outcome.length(), sha256Hex(outcome), artifactId, headPart, tailPart);
            return toolMessage(request.id(), request.name(), offloaded);
        } catch (RuntimeException e) {
            log.warn("Tool-result offload failed, fallback to inline memory persistence: tool={}, memoryId={}",
                    request.name(), memoryId, e);
            return null;
        }
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private List<AiChatMessage> buildModelMessages(List<AiChatMessage> fullMessages,
                                                   AiChatMessage currentUserMessage,
                                                   TaskExecutionState taskExecutionState) {
        ContextSnapshot snapshot = ContextSnapshot.from(fullMessages, currentUserMessage);
        return contextAssembler.assemble(snapshot, taskExecutionState);
    }

    private List<AiChatMessage> trimForTotalModelLimit(List<AiChatMessage> messages,
                                                        AiChatMessage currentUserMessage) {
        if (messages == null
                || messages.isEmpty()
                || modelContextMaxTotalMessages <= 0
                || messages.size() <= modelContextMaxTotalMessages) {
            return messages == null ? List.of() : new ArrayList<>(messages);
        }
        return contextBudgetPolicy.trimForTotalLimit(messages, currentUserMessage);
    }

    private List<AiChatMessage> maybeInjectIndexedRehydration(List<AiChatMessage> modelMessages,
                                                               AiChatMessage currentUserMessage,
                                                               Object memoryId) {
        if (!enableToolResultRehydrate
                || toolResultArtifactStore == null
                || memoryId == null
                || modelMessages == null
                || modelMessages.isEmpty()) {
            return modelMessages == null ? List.of() : new ArrayList<>(modelMessages);
        }

        int selectLimit = toolResultRehydrateMaxPerRound <= 0
                ? Integer.MAX_VALUE
                : toolResultRehydrateMaxPerRound;
        int fetchLimit = computeIndexedRehydrateFetchLimit(toolResultRehydrateMaxPerRound);
        List<ToolResultArtifactRef> refs = selectRefsForIndexedRehydration(
                toolResultArtifactStore.recent(memoryId, fetchLimit),
                modelMessages,
                currentUserMessage,
                selectLimit
        );
        if (refs == null || refs.isEmpty()) {
            return new ArrayList<>(modelMessages);
        }

        List<AiChatMessage> appended = new ArrayList<>(modelMessages);
        Set<String> injectedArtifactIds = collectRehydratedArtifactIds(modelMessages);
        int injected = 0;
        for (int i = 0; i < refs.size(); i++) {
            if (injected >= selectLimit) {
                break;
            }
            ToolResultArtifactRef ref = refs.get(i);
            if (ref == null || ref.artifactId() == null) {
                continue;
            }
            if (!injectedArtifactIds.add(ref.artifactId())) {
                continue;
            }
            Optional<String> loaded = toolResultArtifactStore.load(ref.artifactId());
            if (loaded.isEmpty()) {
                continue;
            }
            String payload = loaded.get();
            if (payload.length() > toolResultRehydrateMaxChars) {
                continue;
            }
            String toolName = ref.toolName() == null || ref.toolName().isBlank()
                    ? "unknown_tool" : ref.toolName();
            String rehydrated = """
                    [Tool Result Rehydrated]
                    source=index
                    tool=%s
                    artifactId=%s
                    originalChars=%d
                    text:
                    %s
                    """
                    .formatted(
                            toolName,
                            ref.artifactId(),
                            ref.originalChars() > 0 ? ref.originalChars() : payload.length(),
                            payload
                    );
            appended.add(toolMessage("indexed_rehydrate_" + injected, toolName, rehydrated));
            injected++;
        }
        if (injected == 0) {
            return new ArrayList<>(modelMessages);
        }
        return trimForTotalModelLimit(appended, currentUserMessage);
    }

    private static Set<String> collectRehydratedArtifactIds(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Set.of();
        }
        Set<String> artifactIds = new HashSet<>();
        for (AiChatMessage message : messages) {
            if (message == null || message.role() != AiChatMessage.Role.TOOL) {
                continue;
            }
            String content = message.content();
            if (content == null || !content.startsWith("[Tool Result Rehydrated]")) {
                continue;
            }
            String artifactId = extractArtifactId(content);
            if (artifactId != null) {
                artifactIds.add(artifactId);
            }
        }
        return artifactIds;
    }

    private static int computeIndexedRehydrateFetchLimit(int maxCount) {
        if (maxCount <= 0) {
            return INDEXED_REHYDRATE_FETCH_LIMIT_MAX;
        }
        long suggested = Math.max((long) maxCount * 4L, INDEXED_REHYDRATE_FETCH_LIMIT_MIN);
        return (int) Math.min(suggested, INDEXED_REHYDRATE_FETCH_LIMIT_MAX);
    }

    private static List<ToolResultArtifactRef> selectRefsForIndexedRehydration(
            List<ToolResultArtifactRef> refs,
            List<AiChatMessage> modelMessages,
            AiChatMessage currentUserMessage,
            int maxCount) {
        return IndexedRehydrateSelector.select(refs, modelMessages, currentUserMessage, maxCount);
    }

    private List<AiChatMessage> applyApproxTokenBudget(List<AiChatMessage> messages,
                                                        AiChatMessage currentUserMessage) {
        if (messages == null || messages.isEmpty() || modelContextMaxApproxTokens <= 0) {
            return messages == null ? List.of() : new ArrayList<>(messages);
        }
        List<AiChatMessage> budgetedRuntimeMessages = contextBudgetPolicy.applyApproxTokenBudget(
                messages,
                currentUserMessage
        );
        if (budgetedRuntimeMessages == null || budgetedRuntimeMessages.isEmpty()) {
            return tail(messages, 1);
        }
        return new ArrayList<>(budgetedRuntimeMessages);
    }

    private static String extractArtifactId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.startsWith("artifactId=")) {
                continue;
            }
            String id = trimmed.substring("artifactId=".length()).trim();
            if (IndexedRehydrateSelector.isValidArtifactId(id)) {
                return id;
            }
        }
        return null;
    }

    private static String toolBatchFingerprint(List<AiToolCall> requests) {
        return requests.stream()
                .map(request -> request.name() + "|" + String.valueOf(request.arguments()))
                .collect(Collectors.joining("||"));
    }

    private static String resolveToolCallId(AiToolCall request) {
        if (request != null && request.id() != null && !request.id().isBlank()) {
            return request.id();
        }
        return "call_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static List<AiChatMessage> tail(List<AiChatMessage> source, int size) {
        if (source == null || size <= 0 || source.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, source.size() - size);
        return new ArrayList<>(source.subList(from, source.size()));
    }

    private static MessageListState initializeMessageList(AiMemory memory) {
        if (memory == null) {
            return new MessageListState();
        }
        return new MessageListState(copyOwnedMessages(memory.messages()));
    }

    private static List<AiChatMessage> copyOwnedMessages(List<AiChatMessage> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source);
    }

    private static void persistToMemory(AiMemory memory, AiChatMessage message) {
        if (memory == null || message == null) {
            return;
        }
        memory.add(message);
    }

    private static AiChatMessage toolMessage(String toolCallId, String toolName, String content) {
        return new AiChatMessage(AiChatMessage.Role.TOOL, content, toolName, toolCallId, List.of());
    }

    private static final class MessageListState {
        private final List<AiChatMessage> messages;

        private MessageListState() {
            this.messages = new ArrayList<>();
        }

        private MessageListState(List<AiChatMessage> initialMessages) {
            this.messages = initialMessages == null ? new ArrayList<>() : initialMessages;
        }

        private List<AiChatMessage> messages() {
            return messages;
        }

        private int size() {
            return messages.size();
        }

        private void addAtHead(AiChatMessage message) {
            if (message == null) {
                return;
            }
            messages.add(0, message);
        }

        private void appendForModel(AiChatMessage message) {
            if (message == null) {
                return;
            }
            messages.add(message);
        }

        private void appendAndPersist(AiChatMessage message, AiMemory memory) {
            appendForModel(message);
            persistToMemory(memory, message);
        }
    }

    private static void ensureSystemMessageInMemory(AiMemory memory, Object memoryId, AiChatMessage message) {
        if (memory instanceof AiSystemMessageMemory systemMessageMemory) {
            systemMessageMemory.ensureSystemMessage(message.content());
            return;
        }
        ReentrantLock lock = lockForSystemMessage(memoryId);
        lock.lock();
        try {
            boolean exists = memory.messages().stream()
                    .anyMatch(chatMessage -> chatMessage != null
                            && chatMessage.role() == AiChatMessage.Role.SYSTEM
                            && Objects.equals(chatMessage.content(), message.content()));
            if (!exists) {
                memory.add(AiChatMessage.system(message.content()));
            }
        } finally {
            lock.unlock();
        }
    }

    private static ReentrantLock lockForSystemMessage(Object memoryId) {
        String key = String.valueOf(memoryId);
        int index = Math.floorMod(key.hashCode(), SYSTEM_MESSAGE_LOCK_STRIPES);
        return SYSTEM_MESSAGE_LOCKS[index];
    }

    private static ReentrantLock[] createSystemMessageLocks() {
        ReentrantLock[] locks = new ReentrantLock[SYSTEM_MESSAGE_LOCK_STRIPES];
        for (int i = 0; i < SYSTEM_MESSAGE_LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private void logContextGovernance(int iteration,
                                      List<AiChatMessage> beforeBudget,
                                      List<AiChatMessage> afterFirstBudget,
                                      List<AiChatMessage> afterIndexedRehydrate,
                                      List<AiChatMessage> finalMessages) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "context_governance iter={} base.count={} base.tokens~={} first_budget.count={} first_budget.tokens~={} "
                        + "indexed_rehydrate.count={} indexed_rehydrate.tokens~={} indexed_rehydrate.blocks={} "
                        + "final.count={} final.tokens~={} final.rehydrated.blocks={} final.offload.cards={}",
                iteration,
                sizeOf(beforeBudget),
                estimateApproxTokens(beforeBudget),
                sizeOf(afterFirstBudget),
                estimateApproxTokens(afterFirstBudget),
                sizeOf(afterIndexedRehydrate),
                estimateApproxTokens(afterIndexedRehydrate),
                countRehydratedBlocks(afterIndexedRehydrate),
                sizeOf(finalMessages),
                estimateApproxTokens(finalMessages),
                countRehydratedBlocks(finalMessages),
                countOffloadCards(finalMessages)
        );
    }

    private static int sizeOf(List<AiChatMessage> messages) {
        return messages == null ? 0 : messages.size();
    }

    private static int estimateApproxTokens(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (AiChatMessage message : messages) {
            total += estimateApproxTokens(message);
        }
        return total;
    }

    private static int estimateApproxTokens(AiChatMessage message) {
        String text = extractTextForBudgetLog(message);
        if (text == null || text.isEmpty()) {
            return 8;
        }
        return 8 + (text.length() + 3) / 4;
    }

    private static String extractTextForBudgetLog(AiChatMessage message) {
        if (message == null) {
            return "";
        }
        if (message.role() == AiChatMessage.Role.ASSISTANT
                && (message.content() == null || message.content().isBlank())
                && message.toolCalls() != null
                && !message.toolCalls().isEmpty()) {
            return message.toolCalls().toString();
        }
        return message.content() == null ? "" : message.content();
    }

  private static int countRehydratedBlocks(List<AiChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (AiChatMessage message : messages) {
      if (message == null) {
        continue;
      }
      String text = message.content();
      if (text != null && text.contains("[Tool Result Rehydrated]")) {
        count++;
      }
    }
    return count;
  }

    private static int countOffloadCards(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (AiChatMessage message : messages) {
            if (message != null && message.role() == AiChatMessage.Role.TOOL) {
                String text = message.content();
                if (text != null && text.contains("[Tool Result Offloaded]")) {
                    count++;
                }
            }
        }
        return count;
    }

    private void publishIterationEvent(Object memoryId, int iteration, TaskExecutionState taskState) {
        if (executionEventPort == null || memoryId == null) {
            return;
        }
        String sessionId = memoryId.toString();
        String taskPlan = taskState != null ? taskState.plan() : "";
        String currentTool = taskState != null ? taskState.inProgress() : "";

        var event = new com.openmanus.domain.model.AgentExecutionEvent();
        event.setSessionId(sessionId);
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setAgentName("execution_coordinator");
        event.setAgentType("AGENT_ITERATION");
        event.setEventType(com.openmanus.domain.model.AgentExecutionEvent.EventType.AGENT_START);
        event.setStatus("RUNNING");
        event.setMetadata(java.util.Map.of(
            "iteration", iteration,
            "taskPlan", taskPlan != null ? taskPlan : "",
            "currentTool", currentTool != null ? currentTool : ""
        ));

        executionEventPort.recordCustomEvent(event);
        log.debug("Published iteration event: sessionId={}, iteration={}", sessionId, iteration);
    }
}
