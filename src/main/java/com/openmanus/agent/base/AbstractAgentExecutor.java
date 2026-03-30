package com.openmanus.agent.base;

import com.openmanus.agent.context.ModelContextBudgeter;
import com.openmanus.infra.memory.ToolResultArtifactStore;
import com.openmanus.infra.memory.PersistentChatMemory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.ReentrantLock;

import static com.openmanus.infra.log.LogMarkers.TO_FRONTEND;
import java.util.*;
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

        ChatModel chatModel;
        SystemMessage systemMessage;
        ChatMemoryProvider chatMemoryProvider;
        boolean enableToolResultCompaction = false;
        int memoryToolResultMaxChars = 4000;
        int compactToolResultHeadChars = 300;
        int compactToolResultTailChars = 200;
        int modelContextMaxMessages = 0;
        int modelContextMaxTotalMessages = 0;
        int modelContextMaxApproxTokens = 0;
        int maxIterations = 0;
        int maxExecutionSeconds = 0;
        int repeatedToolCallThreshold = 0;
        boolean enableToolResultOffload = false;
        int toolResultOffloadMinChars = 12000;
        int toolResultOffloadHeadChars = 240;
        int toolResultOffloadTailChars = 160;
        boolean enableToolResultRehydrate = false;
        int toolResultRehydrateMaxChars = 8000;
        int toolResultRehydrateMaxPerRound = 0;
        ToolResultArtifactStore toolResultArtifactStore;
        final Map<String, Map.Entry<ToolSpecification, ToolExecutor>> tools = new HashMap<>();

        /**
         * Sets the chat model to be used by the agent.
         * @param model The {@link ChatModel} instance.
         * @return The builder instance for chaining.
         */
        public B chatModel(ChatModel model) {
            this.chatModel = model;
            return result();
        }

        /**
         * Sets the chat memory provider used to persist full chat messages by memory id.
         * @param chatMemoryProvider The {@link ChatMemoryProvider}.
         * @return The builder instance for chaining.
         */
        public B chatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
            this.chatMemoryProvider = chatMemoryProvider;
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
        public B toolResultArtifactStore(ToolResultArtifactStore store) {
            this.toolResultArtifactStore = store;
            return result();
        }

        /**
         * Adds a pre-configured tool to the agent.
         * @param entry A map entry containing the tool's specification and its executor.
         * @return The builder instance for chaining.
         */
        public B tool(Map.Entry<ToolSpecification, ToolExecutor> entry) {
            tools.put(entry.getKey().name(), entry);
            return result();
        }

        /**
         * Scans an object for methods annotated with {@link dev.langchain4j.agent.tool.Tool}
         * and adds them as executable tools for the agent.
         *
         * @param objectWithTools The object containing tool methods.
         * @return The builder instance for chaining.
         */
        public B toolFromObject( Object objectWithTools ) {
            ToolSpecifications.toolSpecificationsFrom(objectWithTools).forEach(spec -> {
                Method method = findMethod(spec, objectWithTools);
                ToolExecutor executor = new DefaultToolExecutor(objectWithTools, method);
                tools.put(spec.name(), Map.entry(spec, executor));
            });
            return result();
        }

        /**
         * Finds the corresponding {@link Method} on a tool object that matches a {@link ToolSpecification}.
         * This implementation matches based on the method name and the number of parameters,
         * which is robust enough for most cases, including simple method overloading.
         *
         * @param spec The tool specification.
         * @param objectWithTools The object containing the method.
         * @return The found {@link Method}.
         * @throws IllegalStateException if no matching method is found.
         */
        private Method findMethod(ToolSpecification spec, Object objectWithTools) {
            int expectedParamCount = spec.parameters() == null || spec.parameters().properties() == null
                    ? 0
                    : spec.parameters().properties().size();
            // Find a method on the object that matches the tool specification's name and parameter count.
            // This is a robust way to handle most cases, including simple method overloading.
            return Arrays.stream(objectWithTools.getClass().getMethods())
                    .filter(method -> method.getName().equals(spec.name()))
                    .filter(method -> method.getParameterCount() == expectedParamCount)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("Method '%s' with %d parameters not found on class %s",
                                    spec.name(), expectedParamCount, objectWithTools.getClass().getName())));
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

        /**
         * Sets the system message that provides instructions and context to the agent.
         * @param message The {@link SystemMessage}.
         * @return The builder instance for chaining.
         */
        public B systemMessage(SystemMessage message) {
            this.systemMessage = message;
            return result();
        }
    }

    private final ChatModel chatModel;
    private final SystemMessage systemMessage;
    private final ChatMemoryProvider chatMemoryProvider;
    private final boolean enableToolResultCompaction;
    private final int memoryToolResultMaxChars;
    private final int compactToolResultHeadChars;
    private final int compactToolResultTailChars;
    private final int modelContextMaxMessages;
    private final int modelContextMaxTotalMessages;
    private final int modelContextMaxApproxTokens;
    private final int maxIterations;
    private final int maxExecutionSeconds;
    private final int repeatedToolCallThreshold;
    private final boolean enableToolResultOffload;
    private final int toolResultOffloadMinChars;
    private final int toolResultOffloadHeadChars;
    private final int toolResultOffloadTailChars;
    private final boolean enableToolResultRehydrate;
    private final int toolResultRehydrateMaxChars;
    private final int toolResultRehydrateMaxPerRound;
    private final ToolResultArtifactStore toolResultArtifactStore;
    private final Map<String, Map.Entry<ToolSpecification, ToolExecutor>> tools;
    private final List<ToolSpecification> toolSpecifications;
    private static final int SYSTEM_MESSAGE_LOCK_STRIPES = 1024;
    private static final int LOOP_CONTINUITY_MIN_PREVIEW_CHARS = 128;
    private static final ReentrantLock[] SYSTEM_MESSAGE_LOCKS = createSystemMessageLocks();

    public AbstractAgentExecutor( Builder<B> builder ) {
        super( builder );
        this.chatModel = builder.chatModel;
        this.systemMessage = builder.systemMessage;
        this.chatMemoryProvider = builder.chatMemoryProvider;
        this.enableToolResultCompaction = builder.enableToolResultCompaction;
        this.memoryToolResultMaxChars = builder.memoryToolResultMaxChars;
        this.compactToolResultHeadChars = builder.compactToolResultHeadChars;
        this.compactToolResultTailChars = builder.compactToolResultTailChars;
        this.modelContextMaxMessages = builder.modelContextMaxMessages;
        this.modelContextMaxTotalMessages = builder.modelContextMaxTotalMessages;
        this.modelContextMaxApproxTokens = builder.modelContextMaxApproxTokens;
        this.maxIterations = builder.maxIterations;
        this.maxExecutionSeconds = builder.maxExecutionSeconds;
        this.repeatedToolCallThreshold = builder.repeatedToolCallThreshold;
        this.enableToolResultOffload = builder.enableToolResultOffload;
        this.toolResultOffloadMinChars = builder.toolResultOffloadMinChars;
        this.toolResultOffloadHeadChars = builder.toolResultOffloadHeadChars;
        this.toolResultOffloadTailChars = builder.toolResultOffloadTailChars;
        this.enableToolResultRehydrate = builder.enableToolResultRehydrate;
        this.toolResultRehydrateMaxChars = builder.toolResultRehydrateMaxChars;
        this.toolResultRehydrateMaxPerRound = builder.toolResultRehydrateMaxPerRound;
        this.toolResultArtifactStore = builder.toolResultArtifactStore;
        this.tools = builder.tools;
        this.toolSpecifications = builder.tools.values().stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Executes the agent's ReAct loop to process a user request.
     * The method initiates a conversation with the language model, executes tools as directed by the model,
     * and continues this cycle until the model provides a final answer or the maximum number of iterations is reached.
     *
     * @param toolExecutionRequest The initial request that triggers the agent. The user's prompt is expected
     *                             to be in the 'context' field of the JSON arguments.
     * @param memoryId A unique identifier for the conversation memory, allowing the agent to maintain context
     *                 across multiple turns (if the underlying tools support it).
     * @return The final text response from the agent.
     * @throws RuntimeException if the agent exceeds the maximum number of iterations or if the model fails to respond.
     */
    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.info("Starting agent execution with request: {}", toolExecutionRequest.toString());
        log.info("MemoryId: {}", memoryId != null ? memoryId.toString() : "null");

        // 1. Prepare message history (full chat memory if available)
        ChatMemory memory = null;
        if (chatMemoryProvider != null && memoryId != null) {
            memory = chatMemoryProvider.get(memoryId);
        }

        MessageListState messageList = initializeMessageList(memory);

        boolean hasSystemMessage = messageList.messages().stream().anyMatch(message -> message instanceof SystemMessage);
        if (!hasSystemMessage && systemMessage != null) {
            messageList.addAtHead(systemMessage);
            if (memory != null) {
                ensureSystemMessageInMemory(memory, memoryId, systemMessage);
            }
        }
        log.info("Context prepared from chat memory: memoryId={}, messageCount={}",
                memoryId,
                messageList.size());

        UserMessage userMessage = extractUserMessageFromRequest(toolExecutionRequest);
        messageList.appendAndPersist(userMessage, memory);

        // 2. Start the ReAct loop
        long startNs = System.nanoTime();
        String lastToolBatchFingerprint = null;
        int repeatedToolBatchCount = 0;
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

            // 3. Call the model with the current conversation history and available tools
            // The standard ChatModel interface uses .generate(), which returns a Response<AiMessage>.
            List<ChatMessage> modelMessagesBeforeBudget = buildModelMessages(messageList.messages(), userMessage);
            List<ChatMessage> modelMessagesAfterFirstBudget = applyApproxTokenBudget(modelMessagesBeforeBudget, userMessage);
            List<ChatMessage> modelMessagesAfterIndexedRehydrate =
                    maybeInjectIndexedRehydration(modelMessagesAfterFirstBudget, userMessage, memoryId);
            List<ChatMessage> modelMessages = applyApproxTokenBudget(modelMessagesAfterIndexedRehydrate, userMessage);
            logContextGovernance(
                    i + 1,
                    modelMessagesBeforeBudget,
                    modelMessagesAfterFirstBudget,
                    modelMessagesAfterIndexedRehydrate,
                    modelMessages
            );
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(modelMessages)
                    .toolSpecifications(toolSpecifications)
                    .build();
            ChatResponse response = chatModel.chat(chatRequest);
            if (response == null || response.aiMessage() == null) {
                throw new RuntimeException("LLM failed to generate a response.");
            }
            AiMessage aiMessage = response.aiMessage();
            messageList.appendAndPersist(aiMessage, memory); // Add AI response and persist.

            // 4. Analyze the response
            if (!aiMessage.hasToolExecutionRequests()) {
                // If the AI message does not contain a tool execution request, it's considered the final answer.
                log.info("Agent finished with a final answer.");
                return aiMessage.text(); // Task complete, return the final answer.
            }

            // 5. Execute the requested tool(s)
            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
            String currentFingerprint = toolBatchFingerprint(requests);
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
            for (ToolExecutionRequest request : requests) {
                log.debug("Executing tool: {}", request.name());
                
                // 通知前端工具执行开始
                log.info(TO_FRONTEND, "│  🔧 执行工具: {}", request.name());
                
                Map.Entry<ToolSpecification, ToolExecutor> toolEntry = tools.get(request.name());
                if (toolEntry != null) {
                    // Execute the tool and get the outcome.
                    String outcome = toolEntry.getValue().execute(request, memoryId);
                    ToolExecutionResultMessage persistedToolResult = compactForMemoryIfNeeded(request, outcome, memoryId);
                    ToolExecutionResultMessage modelToolResult = modelToolResultForLoop(request, outcome, persistedToolResult);
                    messageList.appendForModel(modelToolResult); // For next-iteration model context.
                    persistToMemory(memory, persistedToolResult);
                    
                    // 通知前端工具执行完成
                    log.info(TO_FRONTEND, "│  ✔️  工具执行完成: {}", request.name());
                } else {
                    String outcome = "Tool not found: " + request.name();
                    ToolExecutionResultMessage persistedToolResult = compactForMemoryIfNeeded(request, outcome, memoryId);
                    ToolExecutionResultMessage modelToolResult = modelToolResultForLoop(request, outcome, persistedToolResult);
                    messageList.appendForModel(modelToolResult);
                    persistToMemory(memory, persistedToolResult);
                }
            }
        }

    }

    /**
     * Extracts the user's core message from the initial {@link ToolExecutionRequest}.
     * Unified mode defaults to plain-text arguments.
     * For backward compatibility, only strict legacy shape {"context":"..."} is unpacked.
     *
     * @param toolExecutionRequest The initial request to the agent.
     * @return A {@link UserMessage} containing the extracted prompt.
     */
    private UserMessage extractUserMessageFromRequest(ToolExecutionRequest toolExecutionRequest) {
        String arguments = toolExecutionRequest.arguments();
        if (arguments == null) {
            throw new IllegalArgumentException("toolExecutionRequest.arguments cannot be null or blank");
        }
        try {
            // Backward compatibility:
            // if arguments is legacy JSON shape {"context":"..."},
            // use the embedded context; otherwise keep raw plain text.
            var jsonArgs = com.google.gson.JsonParser.parseString(arguments).getAsJsonObject();
            boolean strictLegacyShape = jsonArgs.size() == 1
                    && jsonArgs.has("context")
                    && jsonArgs.get("context").isJsonPrimitive()
                    && jsonArgs.get("context").getAsJsonPrimitive().isString();
            if (strictLegacyShape) {
                arguments = jsonArgs.get("context").getAsString();
            }
        } catch (Exception e) {
            // Plain-text arguments are the default in unified workflow.
        }
        if (arguments.isBlank()) {
            throw new IllegalArgumentException("toolExecutionRequest.arguments cannot be null or blank");
        }
        return UserMessage.from(arguments);
    }

    private ToolExecutionResultMessage compactForMemoryIfNeeded(ToolExecutionRequest request,
                                                                String outcome,
                                                                Object memoryId) {
        ToolExecutionResultMessage offloaded = offloadForMemoryIfNeeded(request, outcome, memoryId);
        if (offloaded != null) {
            return offloaded;
        }
        if (!enableToolResultCompaction) {
            return ToolExecutionResultMessage.from(request, outcome);
        }
        if (outcome == null || outcome.length() <= memoryToolResultMaxChars) {
            return ToolExecutionResultMessage.from(request, outcome);
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

        return ToolExecutionResultMessage.from(request, compacted);
    }

    private ToolExecutionResultMessage modelToolResultForLoop(ToolExecutionRequest request,
                                                              String outcome,
                                                              ToolExecutionResultMessage persistedToolResult) {
        if (persistedToolResult == null) {
            return ToolExecutionResultMessage.from(request, outcome);
        }
        String persistedText = persistedToolResult.text();
        if (persistedText == null) {
            return ToolExecutionResultMessage.from(request, outcome);
        }
        if (persistedText.startsWith("[Tool Result Compacted]")) {
            return persistedToolResult;
        }
        if (persistedText.startsWith("[Tool Result Offloaded]")) {
            return buildLoopContinuityToolResult(request, outcome, persistedText);
        }
        return ToolExecutionResultMessage.from(request, outcome);
    }

    private ToolExecutionResultMessage buildLoopContinuityToolResult(ToolExecutionRequest request,
                                                                     String outcome,
                                                                     String persistedText) {
        if (outcome == null) {
            return ToolExecutionResultMessage.from(request, persistedText);
        }
        if (outcome.length() <= toolResultRehydrateMaxChars) {
            return ToolExecutionResultMessage.from(request, outcome);
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
                        persistedText.startsWith("[Tool Result Offloaded]") ? "[Tool Result Offloaded]" : "[Tool Result Compacted]",
                        outcome.length(),
                        sha256Hex(outcome),
                        artifactId == null ? "n/a" : artifactId,
                        headPart,
                        tailPart
                );
        return ToolExecutionResultMessage.from(request, continuity);
    }

    private ToolExecutionResultMessage offloadForMemoryIfNeeded(ToolExecutionRequest request,
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
            return ToolExecutionResultMessage.from(request, offloaded);
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

    private List<ChatMessage> buildModelMessages(List<ChatMessage> fullMessages,
                                                 UserMessage currentUserMessage) {
        if (fullMessages.isEmpty()) {
            return List.of(currentUserMessage);
        }

        int currentUserIndex = findMessageIndexByIdentity(fullMessages, currentUserMessage);
        if (currentUserIndex < 0) {
            List<ChatMessage> historyOnly = new ArrayList<>(trimHistoryForModel(fullMessages));
            historyOnly.add(currentUserMessage);
            return maybeRehydrateToolResults(trimForTotalModelLimit(historyOnly, currentUserMessage));
        }

        List<ChatMessage> historicalMessages = new ArrayList<>(fullMessages.subList(0, currentUserIndex));
        List<ChatMessage> currentTurnMessages = new ArrayList<>(fullMessages.subList(currentUserIndex, fullMessages.size()));

        List<ChatMessage> modelMessages = new ArrayList<>(trimHistoryForModel(historicalMessages));
        modelMessages.addAll(currentTurnMessages);
        return maybeRehydrateToolResults(trimForTotalModelLimit(modelMessages, currentUserMessage));
    }

    private static int findMessageIndexByIdentity(List<ChatMessage> messages, ChatMessage target) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private List<ChatMessage> trimForTotalModelLimit(List<ChatMessage> messages, UserMessage currentUserMessage) {
        if (messages.isEmpty()
                || modelContextMaxTotalMessages <= 0
                || messages.size() <= modelContextMaxTotalMessages) {
            return new ArrayList<>(messages);
        }

        SystemMessage firstSystemMessage = messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .findFirst()
                .orElse(null);
        int userIndex = findMessageIndexByIdentity(messages, currentUserMessage);
        ChatMessage latestToolResult = findLatestToolResultMessage(messages, userIndex);

        List<ChatMessage> fixed = new ArrayList<>();
        Set<ChatMessage> selected = Collections.newSetFromMap(new IdentityHashMap<>());

        if (userIndex >= 0 && selected.size() < modelContextMaxTotalMessages) {
            selected.add(currentUserMessage);
        }

        // In tiny total windows during ReAct tool loops, keep current user + newest tool result
        // to preserve convergence continuity.
        if (latestToolResult != null
                && modelContextMaxTotalMessages <= 2
                && selected.size() < modelContextMaxTotalMessages) {
            selected.add(latestToolResult);
        }

        if (firstSystemMessage != null && selected.size() < modelContextMaxTotalMessages) {
            selected.add(firstSystemMessage);
        }

        if (latestToolResult != null && selected.size() < modelContextMaxTotalMessages) {
            selected.add(latestToolResult);
        }

        for (int i = messages.size() - 1; i >= 0 && selected.size() < modelContextMaxTotalMessages; i--) {
            ChatMessage candidate = messages.get(i);
            selected.add(candidate);
        }

        for (ChatMessage message : messages) {
            if (selected.contains(message)) {
                fixed.add(message);
            }
        }
        if (fixed.size() > modelContextMaxTotalMessages) {
            return tail(fixed, modelContextMaxTotalMessages);
        }
        return fixed;
    }

    private List<ChatMessage> maybeRehydrateToolResults(List<ChatMessage> messages) {
        if (!enableToolResultRehydrate
                || toolResultArtifactStore == null
                || messages == null
                || messages.isEmpty()) {
            return messages == null ? List.of() : new ArrayList<>(messages);
        }
        List<Integer> candidateIndexes = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message instanceof ToolExecutionResultMessage toolResult
                    && extractArtifactId(toolResult.text()) != null) {
                candidateIndexes.add(i);
            }
        }
        if (candidateIndexes.isEmpty()) {
            return new ArrayList<>(messages);
        }
        int maxPerRound = toolResultRehydrateMaxPerRound <= 0
                ? candidateIndexes.size()
                : Math.min(toolResultRehydrateMaxPerRound, candidateIndexes.size());
        Set<Integer> selectedIndexes = new HashSet<>();
        for (int i = candidateIndexes.size() - 1; i >= 0 && selectedIndexes.size() < maxPerRound; i--) {
            selectedIndexes.add(candidateIndexes.get(i));
        }
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (selectedIndexes.contains(i) && message instanceof ToolExecutionResultMessage toolResult) {
                result.add(rehydrateToolResultMessage(toolResult));
            } else {
                result.add(message);
            }
        }
        return result;
    }

    private ChatMessage rehydrateToolResultMessage(ToolExecutionResultMessage toolResult) {
        String artifactId = extractArtifactId(toolResult.text());
        if (artifactId == null) {
            return toolResult;
        }
        Optional<String> loaded = toolResultArtifactStore.load(artifactId);
        if (loaded.isEmpty()) {
            return toolResult;
        }
        String payload = loaded.get();
        if (payload.length() > toolResultRehydrateMaxChars) {
            return toolResult;
        }
        String rehydrated = """
                [Tool Result Rehydrated]
                tool=%s
                artifactId=%s
                originalChars=%d
                text:
                %s
                """
                .formatted(toolResult.toolName(), artifactId, payload.length(), payload);
        return ToolExecutionResultMessage.from(toolResult.id(), toolResult.toolName(), rehydrated);
    }

    private List<ChatMessage> maybeInjectIndexedRehydration(List<ChatMessage> modelMessages,
                                                            UserMessage currentUserMessage,
                                                            Object memoryId) {
        if (!enableToolResultRehydrate
                || toolResultArtifactStore == null
                || memoryId == null
                || modelMessages == null
                || modelMessages.isEmpty()) {
            return modelMessages == null ? List.of() : new ArrayList<>(modelMessages);
        }
        boolean hasOffloadedCard = modelMessages.stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .map(message -> (ToolExecutionResultMessage) message)
                .map(ToolExecutionResultMessage::text)
                .anyMatch(text -> text != null && text.contains("artifactId=sha256:"));
        if (hasOffloadedCard) {
            return new ArrayList<>(modelMessages);
        }
        int maxCount = toolResultRehydrateMaxPerRound <= 0 ? 1 : toolResultRehydrateMaxPerRound;
        int fetchLimit = Math.max(maxCount * 4, 8);
        List<ToolResultArtifactStore.ArtifactRef> refs =
                selectRefsForIndexedRehydration(toolResultArtifactStore.recent(memoryId, fetchLimit),
                        modelMessages,
                        currentUserMessage,
                        maxCount);
        if (refs == null || refs.isEmpty()) {
            return new ArrayList<>(modelMessages);
        }
        List<ChatMessage> appended = new ArrayList<>(modelMessages);
        int injected = 0;
        for (int i = refs.size() - 1; i >= 0; i--) {
            if (injected >= maxCount) {
                break;
            }
            ToolResultArtifactStore.ArtifactRef ref = refs.get(i);
            if (ref == null || ref.artifactId() == null) {
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
                            ref.toolName() == null || ref.toolName().isBlank() ? "unknown_tool" : ref.toolName(),
                            ref.artifactId(),
                            ref.originalChars() > 0 ? ref.originalChars() : payload.length(),
                            payload
                    );
            appended.add(ToolExecutionResultMessage.from(
                    "rehydrate-index-" + ref.artifactId(),
                    ref.toolName() == null || ref.toolName().isBlank() ? "unknown_tool" : ref.toolName(),
                    rehydrated
            ));
            injected++;
        }
        if (injected == 0) {
            return new ArrayList<>(modelMessages);
        }
        return trimForTotalModelLimit(appended, currentUserMessage);
    }

    private static List<ToolResultArtifactStore.ArtifactRef> selectRefsForIndexedRehydration(
            List<ToolResultArtifactStore.ArtifactRef> refs,
            List<ChatMessage> modelMessages,
            UserMessage currentUserMessage,
            int maxCount) {
        if (refs == null || refs.isEmpty() || maxCount <= 0) {
            return List.of();
        }
        Map<String, ToolResultArtifactStore.ArtifactRef> uniqueByArtifactId = new LinkedHashMap<>();
        for (ToolResultArtifactStore.ArtifactRef ref : refs) {
            if (ref == null || ref.artifactId() == null || ref.artifactId().isBlank()) {
                continue;
            }
            ToolResultArtifactStore.ArtifactRef existing = uniqueByArtifactId.get(ref.artifactId());
            if (existing == null || ref.createdAtEpochMs() > existing.createdAtEpochMs()) {
                uniqueByArtifactId.put(ref.artifactId(), ref);
            }
        }
        if (uniqueByArtifactId.isEmpty()) {
            return List.of();
        }
        List<String> queryTerms = extractTerms(currentUserMessage == null ? null : currentUserMessage.singleText());
        Set<String> activeToolNames = extractVisibleToolNames(modelMessages);
        List<ToolResultArtifactStore.ArtifactRef> ranked = new ArrayList<>(uniqueByArtifactId.values());
        ranked.sort((left, right) -> {
            int leftScore = scoreRef(left, queryTerms, activeToolNames);
            int rightScore = scoreRef(right, queryTerms, activeToolNames);
            if (leftScore != rightScore) {
                return Integer.compare(rightScore, leftScore);
            }
            return Long.compare(right.createdAtEpochMs(), left.createdAtEpochMs());
        });
        return ranked.subList(0, Math.min(maxCount, ranked.size()));
    }

    private static int scoreRef(ToolResultArtifactStore.ArtifactRef ref,
                                List<String> queryTerms,
                                Set<String> activeToolNames) {
        if (ref == null) {
            return Integer.MIN_VALUE;
        }
        String toolName = lower(ref.toolName());
        String toolArguments = lower(ref.toolArguments());
        String corpus = toolName + " " + toolArguments;
        int score = 0;
        if (!toolName.isBlank() && activeToolNames.contains(toolName)) {
            score += 4;
        }
        for (String term : queryTerms) {
            if (term.length() < 2) {
                continue;
            }
            if (corpus.contains(term)) {
                score += 2;
            }
        }
        return score;
    }

    private static Set<String> extractVisibleToolNames(List<ChatMessage> modelMessages) {
        if (modelMessages == null || modelMessages.isEmpty()) {
            return Set.of();
        }
        Set<String> toolNames = new HashSet<>();
        for (ChatMessage message : modelMessages) {
            if (message instanceof ToolExecutionResultMessage toolResult) {
                String name = lower(toolResult.toolName());
                if (!name.isBlank()) {
                    toolNames.add(name);
                }
            } else if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    String name = lower(request.name());
                    if (!name.isBlank()) {
                        toolNames.add(name);
                    }
                }
            }
        }
        return toolNames;
    }

    private static List<String> extractTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = lower(text).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+", " ");
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] parts = normalized.trim().split("\\s+");
        List<String> terms = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                terms.add(part);
            }
        }
        return terms;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private List<ChatMessage> applyApproxTokenBudget(List<ChatMessage> messages,
                                                     UserMessage currentUserMessage) {
        return ModelContextBudgeter.applyApproxTokenBudget(messages, currentUserMessage, modelContextMaxApproxTokens);
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
            if (id.startsWith("sha256:") && id.length() >= "sha256:".length() + 8) {
                return id;
            }
        }
        return null;
    }

    private static ChatMessage findLatestToolResultMessage(List<ChatMessage> messages, int currentUserIndex) {
        for (int i = messages.size() - 1; i > currentUserIndex; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof ToolExecutionResultMessage) {
                return message;
            }
        }
        return null;
    }

    private static String toolBatchFingerprint(List<ToolExecutionRequest> requests) {
        return requests.stream()
                .map(request -> request.name() + "|" + String.valueOf(request.arguments()))
                .collect(Collectors.joining("||"));
    }

    /**
     * Trims historical messages for model input.
     * `modelContextMaxMessages` limits historical messages only.
     * When a system message exists in history, keep the first one whenever possible.
     */
    private List<ChatMessage> trimHistoryForModel(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (modelContextMaxMessages <= 0 || history.size() <= modelContextMaxMessages) {
            return new ArrayList<>(history);
        }

        SystemMessage firstSystemMessage = history.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .findFirst()
                .orElse(null);

        if (firstSystemMessage == null) {
            return tail(history, modelContextMaxMessages);
        }

        if (modelContextMaxMessages == 1) {
            return List.of(firstSystemMessage);
        }

        List<ChatMessage> nonSystemMessages = history.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .collect(Collectors.toList());
        List<ChatMessage> trimmed = new ArrayList<>();
        trimmed.add(firstSystemMessage);
        trimmed.addAll(tail(nonSystemMessages, modelContextMaxMessages - 1));
        return trimmed;
    }

    private static List<ChatMessage> tail(List<ChatMessage> source, int size) {
        if (size <= 0 || source.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, source.size() - size);
        return new ArrayList<>(source.subList(from, source.size()));
    }

    private static MessageListState initializeMessageList(ChatMemory memory) {
        if (memory == null) {
            return new MessageListState();
        }
        return new MessageListState(copyOwnedMessages(memory.messages()));
    }

    private static List<ChatMessage> copyOwnedMessages(List<ChatMessage> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChatMessage> copy = new ArrayList<>(source.size());
        for (ChatMessage message : source) {
            if (message != null) {
                copy.add(message);
            }
        }
        return copy;
    }

    private static void persistToMemory(ChatMemory memory, ChatMessage message) {
        if (memory == null || message == null) {
            return;
        }
        memory.add(message);
    }

    /**
     * Local message-list state for one execute call.
     * Runtime should always read from this list to avoid directly coupling model context to framework-owned lists.
     */
    private static final class MessageListState {
        private final List<ChatMessage> messages;

        private MessageListState() {
            this.messages = new ArrayList<>();
        }

        private MessageListState(List<ChatMessage> initialMessages) {
            this.messages = initialMessages == null ? new ArrayList<>() : initialMessages;
        }

        private List<ChatMessage> messages() {
            return messages;
        }

        private int size() {
            return messages.size();
        }

        private void addAtHead(ChatMessage message) {
            if (message == null) {
                return;
            }
            messages.add(0, message);
        }

        private void appendForModel(ChatMessage message) {
            if (message == null) {
                return;
            }
            messages.add(message);
        }

        private void appendAndPersist(ChatMessage message, ChatMemory memory) {
            appendForModel(message);
            persistToMemory(memory, message);
        }
    }

    private static void ensureSystemMessageInMemory(ChatMemory memory, Object memoryId, SystemMessage message) {
        if (memory instanceof PersistentChatMemory persistentChatMemory) {
            persistentChatMemory.ensureSystemMessage(message);
            return;
        }
        ReentrantLock lock = lockForSystemMessage(memoryId);
        lock.lock();
        try {
            boolean exists = memory.messages().stream()
                    .anyMatch(chatMessage -> chatMessage instanceof SystemMessage existing
                            && existing.text().equals(message.text()));
            if (!exists) {
                memory.add(message);
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
                                      List<ChatMessage> beforeBudget,
                                      List<ChatMessage> afterFirstBudget,
                                      List<ChatMessage> afterIndexedRehydrate,
                                      List<ChatMessage> finalMessages) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "context_governance iter={} base.count={} base.tokens~={} first_budget.count={} first_budget.tokens~={} " +
                        "indexed_rehydrate.count={} indexed_rehydrate.tokens~={} indexed_rehydrate.blocks={} " +
                        "final.count={} final.tokens~={} final.rehydrated.blocks={} final.offload.cards={}",
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

    private static int sizeOf(List<ChatMessage> messages) {
        return messages == null ? 0 : messages.size();
    }

    private static int estimateApproxTokens(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateApproxTokens(message);
        }
        return total;
    }

    private static int estimateApproxTokens(ChatMessage message) {
        String text = extractTextForBudgetLog(message);
        if (text == null || text.isEmpty()) {
            return 8;
        }
        return 8 + (text.length() + 3) / 4;
    }

    private static String extractTextForBudgetLog(ChatMessage message) {
        if (message == null) {
            return "";
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        if (message instanceof ToolExecutionResultMessage toolResultMessage) {
            return toolResultMessage.text();
        }
        if (message instanceof AiMessage aiMessage) {
            String text = aiMessage.text();
            if (text != null && !text.isBlank()) {
                return text;
            }
            if (aiMessage.hasToolExecutionRequests()) {
                return aiMessage.toolExecutionRequests().toString();
            }
            return "";
        }
        return message.toString();
    }

    private static int countRehydratedBlocks(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage toolResult) {
                String text = toolResult.text();
                if (text != null && text.contains("[Tool Result Rehydrated]")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countOffloadCards(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage toolResult) {
                String text = toolResult.text();
                if (text != null && text.contains("[Tool Result Offloaded]")) {
                    count++;
                }
            }
        }
        return count;
    }
}
