package com.openmanus.aiframework.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.exception.AiFrameworkException;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatRequestOptions;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiFinishReason;
import com.openmanus.aiframework.runtime.model.AiTokenUsage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.model.AiToolSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime-first provider chat model.
 * Provider protocol mapping lives here so compatibility bridges stay thin.
 */
public class AiProviderChatModel implements AiChatModel {

    private static final String STRUCTURED_OUTPUT_TOOL_NAME = "structured_output";

    private final AiProviderClientRegistry clientRegistry;
    private final ObjectMapper objectMapper;
    private final String defaultModel;
    private final Double defaultTemperature;
    private final Integer defaultMaxTokens;
    private final Integer defaultTimeoutSeconds;
    private final AiProviderType defaultProviderType;

    public AiProviderChatModel(AiProviderClient openAiClient,
                               ObjectMapper objectMapper,
                               String defaultModel,
                               Double defaultTemperature,
                               Integer defaultMaxTokens,
                               Integer defaultTimeoutSeconds) {
        this(new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, openAiClient)),
                objectMapper,
                defaultModel,
                defaultTemperature,
                defaultMaxTokens,
                defaultTimeoutSeconds,
                AiProviderType.OPENAI);
    }

    public AiProviderChatModel(AiProviderClientRegistry clientRegistry,
                               ObjectMapper objectMapper,
                               String defaultModel,
                               Double defaultTemperature,
                               Integer defaultMaxTokens,
                               Integer defaultTimeoutSeconds,
                               AiProviderType defaultProviderType) {
        this.clientRegistry = Objects.requireNonNull(clientRegistry, "clientRegistry cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.defaultModel = defaultModel;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.defaultProviderType = defaultProviderType == null ? AiProviderType.OPENAI : defaultProviderType;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        AiProviderType providerType = resolveProviderType();
        AiProviderClient client = clientRegistry.getClient(providerType);
        String resolvedModel = resolveModel(request);

        ChatRequestEnvelope envelope = ChatRequestEnvelope.builder()
                .providerType(providerType)
                .model(resolvedModel)
                .messages(convertMessages(request, providerType))
                .providerPayload(buildProviderPayload(request, providerType))
                .requestOptions(ChatRequestOptions.builder()
                        .temperature(resolveTemperature(request))
                        .maxTokens(resolveMaxTokens(request))
                        .timeoutSeconds(resolveTimeoutSeconds(request))
                        .stream(false)
                        .build())
                .build();

        ChatResponseEnvelope response = client.chat(envelope);
        if (response == null) {
            throw new AiFrameworkException("Provider returned empty response: provider=" + providerType.name().toLowerCase(Locale.ROOT));
        }
        return convertResponse(response, resolvedModel);
    }

    private AiProviderType resolveProviderType() {
        return defaultProviderType;
    }

    private String resolveModel(AiChatRequest request) {
        if (request.model() != null && !request.model().isBlank()) {
            return request.model();
        }
        return defaultModel;
    }

    private Double resolveTemperature(AiChatRequest request) {
        return request.temperature() == null ? defaultTemperature : request.temperature();
    }

    private Integer resolveMaxTokens(AiChatRequest request) {
        return request.maxOutputTokens() == null ? defaultMaxTokens : request.maxOutputTokens();
    }

    private Integer resolveTimeoutSeconds(AiChatRequest request) {
        return request.timeoutSeconds() == null ? defaultTimeoutSeconds : request.timeoutSeconds();
    }

    private List<ChatMessage> convertMessages(AiChatRequest request, AiProviderType providerType) {
        List<ChatMessage> messages = new ArrayList<>();
        if (request.messages() == null) {
            return messages;
        }
        for (AiChatMessage message : request.messages()) {
            if (message == null || message.role() == null) {
                continue;
            }
            ChatMessage converted = convertMessage(message, providerType);
            if (converted != null) {
                messages.add(converted);
            }
        }
        return messages;
    }

    private ChatMessage convertMessage(AiChatMessage message, AiProviderType providerType) {
        return switch (message.role()) {
            case SYSTEM -> ChatMessage.builder()
                    .role("system")
                    .content(safeText(message.content()))
                    .build();
            case USER -> ChatMessage.builder()
                    .role("user")
                    .name(blankToNull(message.name()))
                    .content(safeText(message.content()))
                    .build();
            case ASSISTANT -> {
                ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                        .role("assistant")
                        .content(safeText(message.content()));
                if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                    builder.toolCalls(toProviderToolCalls(message.toolCalls(), providerType));
                }
                yield builder.build();
            }
            case TOOL -> ChatMessage.builder()
                    .role("tool")
                    .name(blankToNull(message.name()))
                    .toolCallId(blankToNull(message.toolCallId()))
                    .content(safeText(message.content()))
                    .build();
        };
    }

    private ArrayNode toProviderToolCalls(List<AiToolCall> calls, AiProviderType providerType) {
        ArrayNode toolCalls = objectMapper.createArrayNode();
        for (AiToolCall call : calls) {
            if (call == null || call.name() == null || call.name().isBlank()) {
                continue;
            }
            String id = call.id() == null || call.id().isBlank() ? newToolCallId() : call.id();
            if (providerType == AiProviderType.ANTHROPIC) {
                ObjectNode item = toolCalls.addObject();
                item.put("type", "tool_use");
                item.put("id", id);
                item.put("name", call.name());
                item.set("input", toObjectNode(call.arguments()));
                continue;
            }
            if (providerType == AiProviderType.GEMINI) {
                ObjectNode item = toolCalls.addObject();
                item.put("id", id);
                ObjectNode functionCall = item.putObject("functionCall");
                functionCall.put("name", call.name());
                functionCall.set("args", toObjectNode(call.arguments()));
                continue;
            }

            ObjectNode item = toolCalls.addObject();
            item.put("id", id);
            item.put("type", "function");
            ObjectNode function = item.putObject("function");
            function.put("name", call.name());
            function.put("arguments", safeArguments(call.arguments()));
        }
        return toolCalls;
    }

    private JsonNode buildProviderPayload(AiChatRequest request, AiProviderType providerType) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (providerType == AiProviderType.ANTHROPIC) {
            addAnthropicToolDefinitions(payload, request.toolSpecs(), request.responseFormat());
        } else if (providerType == AiProviderType.GEMINI) {
            addGeminiToolDefinitions(payload, request.toolSpecs());
            addGeminiResponseFormat(payload, request.responseFormat());
        } else {
            addOpenAiToolDefinitions(payload, request.toolSpecs());
            addOpenAiResponseFormat(payload, request.responseFormat());
        }
        return payload.isEmpty() ? null : payload;
    }

    private void addOpenAiToolDefinitions(ObjectNode payload, List<AiToolSpec> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return;
        }
        ArrayNode tools = null;
        for (AiToolSpec specification : toolSpecifications) {
            if (specification == null || specification.name() == null || specification.name().isBlank()) {
                continue;
            }
            if (tools == null) {
                tools = payload.putArray("tools");
            }
            ObjectNode tool = tools.addObject();
            tool.put("type", "function");
            ObjectNode function = tool.putObject("function");
            function.put("name", specification.name());
            if (specification.description() != null && !specification.description().isBlank()) {
                function.put("description", specification.description());
            }
            if (specification.inputSchema() != null && !specification.inputSchema().isNull()) {
                function.set("parameters", specification.inputSchema());
            } else {
                function.putObject("parameters").put("type", "object");
            }
        }
    }

    private void addAnthropicToolDefinitions(ObjectNode payload,
                                             List<AiToolSpec> toolSpecifications,
                                             JsonNode responseFormat) {
        ArrayNode tools = null;
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            for (AiToolSpec specification : toolSpecifications) {
                if (specification == null || specification.name() == null || specification.name().isBlank()) {
                    continue;
                }
                if (tools == null) {
                    tools = payload.putArray("tools");
                }
                ObjectNode tool = tools.addObject();
                tool.put("name", specification.name());
                if (specification.description() != null && !specification.description().isBlank()) {
                    tool.put("description", specification.description());
                }
                if (specification.inputSchema() != null && !specification.inputSchema().isNull()) {
                    tool.set("input_schema", specification.inputSchema());
                } else {
                    tool.putObject("input_schema").put("type", "object");
                }
            }
        }

        JsonNode schema = resolveJsonOutputSchemaNode(responseFormat);
        if (schema != null && !schema.isNull() && !schema.isMissingNode()) {
            if (tools == null) {
                tools = payload.putArray("tools");
            }
            ObjectNode outputTool = tools.addObject();
            outputTool.put("name", STRUCTURED_OUTPUT_TOOL_NAME);
            outputTool.put("description", "Return final structured output that matches schema");
            outputTool.set("input_schema", schema);

            ObjectNode toolChoice = payload.putObject("tool_choice");
            toolChoice.put("type", "tool");
            toolChoice.put("name", STRUCTURED_OUTPUT_TOOL_NAME);
        }
    }

    private void addGeminiToolDefinitions(ObjectNode payload, List<AiToolSpec> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return;
        }
        ArrayNode declarations = objectMapper.createArrayNode();
        for (AiToolSpec specification : toolSpecifications) {
            if (specification == null || specification.name() == null || specification.name().isBlank()) {
                continue;
            }
            ObjectNode declaration = declarations.addObject();
            declaration.put("name", specification.name());
            if (specification.description() != null && !specification.description().isBlank()) {
                declaration.put("description", specification.description());
            }
            if (specification.inputSchema() != null && !specification.inputSchema().isNull()) {
                declaration.set("parameters", specification.inputSchema());
            } else {
                declaration.putObject("parameters").put("type", "object");
            }
        }
        if (!declarations.isEmpty()) {
            ObjectNode tool = payload.putArray("tools").addObject();
            tool.set("functionDeclarations", declarations);
        }
    }

    private void addOpenAiResponseFormat(ObjectNode payload, JsonNode responseFormat) {
        if (!isJsonResponseFormat(responseFormat)) {
            return;
        }

        JsonNode schemaContainer = responseFormat.path("jsonSchema");
        JsonNode schema = schemaContainer.path("rootElement");
        if (schema.isMissingNode() || schema.isNull()) {
            schema = schemaContainer.path("schema");
        }

        if (schema.isMissingNode() || schema.isNull()) {
            payload.putObject("response_format").put("type", "json_object");
            return;
        }

        ObjectNode responseFormatNode = payload.putObject("response_format");
        responseFormatNode.put("type", "json_schema");
        ObjectNode jsonSchemaNode = responseFormatNode.putObject("json_schema");
        String schemaName = blankToNull(schemaContainer.path("name").asText(null));
        jsonSchemaNode.put("name", schemaName == null ? "structured_output" : schemaName);
        jsonSchemaNode.set("schema", schema);
    }

    private void addGeminiResponseFormat(ObjectNode payload, JsonNode responseFormat) {
        if (!isJsonResponseFormat(responseFormat)) {
            return;
        }

        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        JsonNode schema = resolveJsonOutputSchemaNode(responseFormat);
        if (schema != null && !schema.isNull() && !schema.isMissingNode()) {
            generationConfig.set("responseSchema", schema);
        }
    }

    private boolean isJsonResponseFormat(JsonNode responseFormat) {
        if (responseFormat == null || !responseFormat.isObject()) {
            return false;
        }
        String type = blankToNull(responseFormat.path("type").asText(null));
        if (type == null) {
            return false;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "json".equals(normalized) || "json_schema".equals(normalized) || "json_object".equals(normalized);
    }

    private JsonNode resolveJsonOutputSchemaNode(JsonNode responseFormat) {
        if (!isJsonResponseFormat(responseFormat)) {
            return null;
        }
        JsonNode schemaContainer = responseFormat.path("jsonSchema");
        if (schemaContainer.isMissingNode() || schemaContainer.isNull()) {
            schemaContainer = responseFormat.path("json_schema");
        }
        if (schemaContainer.isMissingNode() || schemaContainer.isNull()) {
            return null;
        }

        JsonNode rootElement = schemaContainer.path("rootElement");
        if (!rootElement.isMissingNode() && !rootElement.isNull()) {
            return rootElement;
        }

        JsonNode schema = schemaContainer.path("schema");
        if (!schema.isMissingNode() && !schema.isNull()) {
            return schema;
        }

        return null;
    }

    private AiChatResponse convertResponse(ChatResponseEnvelope response, String resolvedRequestModel) {
        List<AiToolCall> toolCalls = toToolCalls(response.getToolCalls());
        AiChatMessage message = new AiChatMessage(
                AiChatMessage.Role.ASSISTANT,
                safeText(response.getContent()),
                null,
                null,
                toolCalls
        );

        JsonNode raw = response.getRawResponse();
        return new AiChatResponse(
                message,
                mapFinishReason(response.getFinishReason()),
                toTokenUsage(response.getUsage()),
                resolveResponseId(raw),
                resolveResponseModelName(raw, resolvedRequestModel),
                raw
        );
    }

    private String resolveResponseModelName(JsonNode raw, String resolvedRequestModel) {
        String modelName = blankToNull(resolvedRequestModel);
        if (raw == null || raw.isNull()) {
            return modelName;
        }
        String direct = raw.path("model").asText(null);
        if (direct == null || direct.isBlank()) {
            direct = raw.path("modelVersion").asText(null);
        }
        return (direct == null || direct.isBlank()) ? modelName : direct;
    }

    private String resolveResponseId(JsonNode raw) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        String direct = raw.path("id").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String nested = raw.path("response").path("id").asText(null);
        return (nested == null || nested.isBlank()) ? null : nested;
    }

    private List<AiToolCall> toToolCalls(List<JsonNode> toolCalls) {
        List<AiToolCall> calls = new ArrayList<>();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return calls;
        }
        for (JsonNode toolCall : toolCalls) {
            if (toolCall == null || toolCall.isNull()) {
                continue;
            }
            String name = resolveToolName(toolCall);
            if (name == null || name.isBlank()) {
                continue;
            }
            String arguments = resolveToolArguments(toolCall);
            String id = resolveToolCallId(toolCall);
            if (id == null || id.isBlank()) {
                id = newToolCallId();
            }
            calls.add(new AiToolCall(id, name, arguments));
        }
        return calls;
    }

    private String resolveToolName(JsonNode toolCall) {
        String name = toolCall.path("function").path("name").asText(null);
        if (name == null || name.isBlank()) {
            name = toolCall.path("functionCall").path("name").asText(null);
        }
        if (name == null || name.isBlank()) {
            name = toolCall.path("name").asText(null);
        }
        return name;
    }

    private String resolveToolArguments(JsonNode toolCall) {
        JsonNode args = toolCall.path("function").path("arguments");
        if (args.isMissingNode() || args.isNull()) {
            args = toolCall.path("arguments");
        }
        if (args.isMissingNode() || args.isNull()) {
            args = toolCall.path("input");
        }
        if (args.isMissingNode() || args.isNull()) {
            args = toolCall.path("functionCall").path("args");
        }
        if (args.isMissingNode() || args.isNull()) {
            args = toolCall.path("args");
        }
        if (args.isMissingNode() || args.isNull()) {
            return "{}";
        }
        return args.isTextual() ? args.asText() : args.toString();
    }

    private String resolveToolCallId(JsonNode toolCall) {
        String id = toolCall.path("id").asText(null);
        if (id == null || id.isBlank()) {
            id = toolCall.path("tool_use_id").asText(null);
        }
        if (id == null || id.isBlank()) {
            id = toolCall.path("toolUseId").asText(null);
        }
        return id;
    }

    private AiFinishReason mapFinishReason(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return null;
        }
        String normalized = finishReason.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "stop", "completed", "end_turn", "stop_sequence" -> AiFinishReason.STOP;
            case "length", "max_tokens", "max_output_tokens" -> AiFinishReason.LENGTH;
            case "tool_calls", "function_call", "tool_use" -> AiFinishReason.TOOL_CALLS;
            case "content_filter", "safety", "blocked" -> AiFinishReason.CONTENT_FILTER;
            default -> AiFinishReason.OTHER;
        };
    }

    private AiTokenUsage toTokenUsage(JsonNode usage) {
        if (usage == null || usage.isNull() || usage.isMissingNode()) {
            return null;
        }
        Integer input = intOrNull(usage.path("prompt_tokens"));
        if (input == null) {
            input = intOrNull(usage.path("input_tokens"));
        }
        if (input == null) {
            input = intOrNull(usage.path("promptTokenCount"));
        }

        Integer output = intOrNull(usage.path("completion_tokens"));
        if (output == null) {
            output = intOrNull(usage.path("output_tokens"));
        }
        if (output == null) {
            output = intOrNull(usage.path("candidatesTokenCount"));
        }

        Integer total = intOrNull(usage.path("total_tokens"));
        if (total == null) {
            total = intOrNull(usage.path("totalTokenCount"));
        }
        if (input == null && output == null && total == null) {
            return null;
        }
        return new AiTokenUsage(input, output, total);
    }

    private Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        if (node.isNumber()) {
            return node.intValue();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private ObjectNode toObjectNode(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isObject()) {
                return (ObjectNode) node;
            }
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set("value", node == null ? objectMapper.nullNode() : node);
            return wrapper;
        } catch (Exception ignored) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("raw", json);
            return wrapper;
        }
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private String safeArguments(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String newToolCallId() {
        return "call_" + UUID.randomUUID().toString().replace("-", "");
    }
}
