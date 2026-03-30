package com.openmanus.aiframework.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatRequestOptions;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class OpenAiFrameworkChatModel implements ChatModel {

    private static final String STRUCTURED_OUTPUT_TOOL_NAME = "structured_output";

    private final AiProviderClientRegistry clientRegistry;
    private final ObjectMapper objectMapper;
    private final String defaultModel;
    private final Double defaultTemperature;
    private final Integer defaultMaxTokens;
    private final Integer defaultTimeoutSeconds;
    private final AiProviderType defaultProviderType;

    public OpenAiFrameworkChatModel(AiProviderClient openAiClient,
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

    public OpenAiFrameworkChatModel(AiProviderClientRegistry clientRegistry,
                                    ObjectMapper objectMapper,
                                    String defaultModel,
                                    Double defaultTemperature,
                                    Integer defaultMaxTokens,
                                    Integer defaultTimeoutSeconds,
                                    AiProviderType defaultProviderType) {
        this.clientRegistry = clientRegistry;
        this.objectMapper = objectMapper;
        this.defaultModel = defaultModel;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.defaultProviderType = defaultProviderType == null ? AiProviderType.OPENAI : defaultProviderType;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        AiProviderType providerType = resolveProviderType();
        AiProviderClient client = clientRegistry.getClient(providerType);

        ChatRequestEnvelope envelope = ChatRequestEnvelope.builder()
                .providerType(providerType)
                .model(resolveModel(request))
                .messages(convertMessages(request, providerType))
                .providerPayload(buildProviderPayload(request, providerType))
                .requestOptions(ChatRequestOptions.builder()
                        .temperature(resolveTemperature(request))
                        .maxTokens(resolveMaxTokens(request))
                        .timeoutSeconds(defaultTimeoutSeconds)
                        .stream(false)
                        .build())
                .build();

        ChatResponseEnvelope response = client.chat(envelope);
        return convertResponse(request, response);
    }

    private AiProviderType resolveProviderType() {
        return defaultProviderType;
    }

    private String resolveModel(ChatRequest request) {
        if (request.modelName() != null && !request.modelName().isBlank()) {
            return request.modelName();
        }
        return defaultModel;
    }

    private Double resolveTemperature(ChatRequest request) {
        return request.temperature() == null ? defaultTemperature : request.temperature();
    }

    private Integer resolveMaxTokens(ChatRequest request) {
        return request.maxOutputTokens() == null ? defaultMaxTokens : request.maxOutputTokens();
    }

    private List<ChatMessage> convertMessages(ChatRequest request, AiProviderType providerType) {
        List<ChatMessage> messages = new ArrayList<>();
        if (request.messages() == null) {
            return messages;
        }
        for (dev.langchain4j.data.message.ChatMessage message : request.messages()) {
            if (message == null) {
                continue;
            }
            ChatMessage converted = convertMessage(message, providerType);
            if (converted != null) {
                messages.add(converted);
            }
        }
        return messages;
    }

    private ChatMessage convertMessage(dev.langchain4j.data.message.ChatMessage message, AiProviderType providerType) {
        ChatMessageType type = message.type();
        if (type == ChatMessageType.SYSTEM) {
            SystemMessage systemMessage = (SystemMessage) message;
            return ChatMessage.builder()
                    .role("system")
                    .content(safeText(systemMessage.text()))
                    .build();
        }
        if (type == ChatMessageType.USER) {
            UserMessage userMessage = (UserMessage) message;
            return ChatMessage.builder()
                    .role("user")
                    .name(blankToNull(userMessage.name()))
                    .content(extractUserText(userMessage))
                    .build();
        }
        if (type == ChatMessageType.AI) {
            AiMessage aiMessage = (AiMessage) message;
            ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                    .role("assistant")
                    .content(safeText(aiMessage.text()));
            if (aiMessage.hasToolExecutionRequests()) {
                builder.toolCalls(toProviderToolCalls(aiMessage.toolExecutionRequests(), providerType));
            }
            return builder.build();
        }
        if (type == ChatMessageType.TOOL_EXECUTION_RESULT) {
            ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) message;
            return ChatMessage.builder()
                    .role("tool")
                    .name(blankToNull(toolResult.toolName()))
                    .toolCallId(blankToNull(toolResult.id()))
                    .content(safeText(toolResult.text()))
                    .build();
        }
        return null;
    }

    private String extractUserText(UserMessage userMessage) {
        if (userMessage.hasSingleText()) {
            return safeText(userMessage.singleText());
        }
        if (userMessage.contents() == null || userMessage.contents().isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        userMessage.contents().forEach(content -> {
            if (content instanceof TextContent textContent) {
                if (!text.isEmpty()) {
                    text.append("\n");
                }
                text.append(safeText(textContent.text()));
            }
        });
        if (!text.isEmpty()) {
            return text.toString();
        }
        return safeText(userMessage.contents().get(0).toString());
    }

    private ArrayNode toProviderToolCalls(List<ToolExecutionRequest> requests, AiProviderType providerType) {
        ArrayNode toolCalls = objectMapper.createArrayNode();
        for (ToolExecutionRequest request : requests) {
            if (request == null || request.name() == null || request.name().isBlank()) {
                continue;
            }
            String id = request.id() == null || request.id().isBlank() ? newToolCallId() : request.id();
            if (providerType == AiProviderType.ANTHROPIC) {
                ObjectNode item = toolCalls.addObject();
                item.put("type", "tool_use");
                item.put("id", id);
                item.put("name", request.name());
                item.set("input", toObjectNode(request.arguments()));
                continue;
            }
            if (providerType == AiProviderType.GEMINI) {
                ObjectNode item = toolCalls.addObject();
                item.put("id", id);
                ObjectNode functionCall = item.putObject("functionCall");
                functionCall.put("name", request.name());
                functionCall.set("args", toObjectNode(request.arguments()));
                continue;
            }

            ObjectNode item = toolCalls.addObject();
            item.put("id", id);
            item.put("type", "function");
            ObjectNode function = item.putObject("function");
            function.put("name", request.name());
            String arguments = request.arguments();
            function.put("arguments", (arguments == null || arguments.isBlank()) ? "{}" : arguments);
        }
        return toolCalls;
    }

    private JsonNode buildProviderPayload(ChatRequest request, AiProviderType providerType) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (providerType == AiProviderType.ANTHROPIC) {
            addAnthropicToolDefinitions(payload, request.toolSpecifications(), request.responseFormat());
        } else if (providerType == AiProviderType.GEMINI) {
            addGeminiToolDefinitions(payload, request.toolSpecifications());
            addGeminiResponseFormat(payload, request.responseFormat());
        } else {
            addOpenAiToolDefinitions(payload, request.toolSpecifications());
            addOpenAiResponseFormat(payload, request.responseFormat());
        }
        return payload.isEmpty() ? null : payload;
    }

    private void addOpenAiToolDefinitions(ObjectNode payload, List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return;
        }
        ArrayNode tools = payload.putArray("tools");
        for (ToolSpecification specification : toolSpecifications) {
            if (specification == null || specification.name() == null || specification.name().isBlank()) {
                continue;
            }
            ObjectNode tool = tools.addObject();
            tool.put("type", "function");
            ObjectNode function = tool.putObject("function");
            function.put("name", specification.name());
            if (specification.description() != null && !specification.description().isBlank()) {
                function.put("description", specification.description());
            }
            if (specification.parameters() != null) {
                function.set("parameters", jsonSchemaElementToNode(specification.parameters()));
            } else {
                function.putObject("parameters").put("type", "object");
            }
        }
    }

    private void addAnthropicToolDefinitions(ObjectNode payload,
                                             List<ToolSpecification> toolSpecifications,
                                             ResponseFormat responseFormat) {
        ArrayNode tools = null;
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            tools = payload.putArray("tools");
            for (ToolSpecification specification : toolSpecifications) {
                if (specification == null || specification.name() == null || specification.name().isBlank()) {
                    continue;
                }
                ObjectNode tool = tools.addObject();
                tool.put("name", specification.name());
                if (specification.description() != null && !specification.description().isBlank()) {
                    tool.put("description", specification.description());
                }
                tool.set("input_schema", specification.parameters() == null
                        ? objectMapper.createObjectNode().put("type", "object")
                        : jsonSchemaElementToNode(specification.parameters()));
            }
        }

        JsonSchema schema = resolveJsonOutputSchema(responseFormat);
        if (schema != null && schema.rootElement() != null) {
            if (tools == null) {
                tools = payload.putArray("tools");
            }
            ObjectNode outputTool = tools.addObject();
            outputTool.put("name", STRUCTURED_OUTPUT_TOOL_NAME);
            outputTool.put("description", "Return final structured output that matches schema");
            outputTool.set("input_schema", jsonSchemaElementToNode(schema.rootElement()));

            ObjectNode toolChoice = payload.putObject("tool_choice");
            toolChoice.put("type", "tool");
            toolChoice.put("name", STRUCTURED_OUTPUT_TOOL_NAME);
        }
    }

    private void addGeminiToolDefinitions(ObjectNode payload, List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return;
        }
        ArrayNode declarations = objectMapper.createArrayNode();
        for (ToolSpecification specification : toolSpecifications) {
            if (specification == null || specification.name() == null || specification.name().isBlank()) {
                continue;
            }
            ObjectNode declaration = declarations.addObject();
            declaration.put("name", specification.name());
            if (specification.description() != null && !specification.description().isBlank()) {
                declaration.put("description", specification.description());
            }
            if (specification.parameters() != null) {
                declaration.set("parameters", jsonSchemaElementToNode(specification.parameters()));
            } else {
                declaration.putObject("parameters").put("type", "object");
            }
        }
        if (!declarations.isEmpty()) {
            ObjectNode tool = payload.putArray("tools").addObject();
            tool.set("functionDeclarations", declarations);
        }
    }

    private void addOpenAiResponseFormat(ObjectNode payload, ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == null) {
            return;
        }
        if (responseFormat.type() != ResponseFormatType.JSON) {
            return;
        }

        JsonSchema schema = responseFormat.jsonSchema();
        if (schema == null || schema.rootElement() == null) {
            payload.putObject("response_format").put("type", "json_object");
            return;
        }

        ObjectNode responseFormatNode = payload.putObject("response_format");
        responseFormatNode.put("type", "json_schema");
        ObjectNode jsonSchemaNode = responseFormatNode.putObject("json_schema");
        jsonSchemaNode.put("name", schema.name() == null || schema.name().isBlank() ? "structured_output" : schema.name());
        jsonSchemaNode.set("schema", jsonSchemaElementToNode(schema.rootElement()));
    }

    private void addGeminiResponseFormat(ObjectNode payload, ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == null || responseFormat.type() != ResponseFormatType.JSON) {
            return;
        }

        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        JsonSchema schema = resolveJsonOutputSchema(responseFormat);
        if (schema != null && schema.rootElement() != null) {
            generationConfig.set("responseSchema", jsonSchemaElementToNode(schema.rootElement()));
        }
    }

    private JsonSchema resolveJsonOutputSchema(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == null) {
            return null;
        }
        if (responseFormat.type() != ResponseFormatType.JSON) {
            return null;
        }
        return responseFormat.jsonSchema();
    }

    private ObjectNode jsonSchemaElementToNode(JsonSchemaElement element) {
        ObjectNode node = objectMapper.createObjectNode();
        if (element instanceof JsonObjectSchema objectSchema) {
            node.put("type", "object");
            if (objectSchema.properties() != null && !objectSchema.properties().isEmpty()) {
                ObjectNode properties = node.putObject("properties");
                objectSchema.properties().forEach((name, child) -> properties.set(name, jsonSchemaElementToNode(child)));
            }
            if (objectSchema.required() != null && !objectSchema.required().isEmpty()) {
                ArrayNode required = node.putArray("required");
                objectSchema.required().forEach(required::add);
            }
            if (objectSchema.additionalProperties() != null) {
                node.put("additionalProperties", objectSchema.additionalProperties());
            }
            if (objectSchema.definitions() != null && !objectSchema.definitions().isEmpty()) {
                ObjectNode defs = node.putObject("$defs");
                objectSchema.definitions().forEach((name, child) -> defs.set(name, jsonSchemaElementToNode(child)));
            }
            putDescription(node, objectSchema.description());
            return node;
        }
        if (element instanceof JsonArraySchema arraySchema) {
            node.put("type", "array");
            if (arraySchema.items() != null) {
                node.set("items", jsonSchemaElementToNode(arraySchema.items()));
            }
            putDescription(node, arraySchema.description());
            return node;
        }
        if (element instanceof JsonStringSchema stringSchema) {
            node.put("type", "string");
            putDescription(node, stringSchema.description());
            return node;
        }
        if (element instanceof JsonIntegerSchema integerSchema) {
            node.put("type", "integer");
            putDescription(node, integerSchema.description());
            return node;
        }
        if (element instanceof JsonNumberSchema numberSchema) {
            node.put("type", "number");
            putDescription(node, numberSchema.description());
            return node;
        }
        if (element instanceof JsonBooleanSchema booleanSchema) {
            node.put("type", "boolean");
            putDescription(node, booleanSchema.description());
            return node;
        }
        if (element instanceof JsonEnumSchema enumSchema) {
            node.put("type", "string");
            ArrayNode values = node.putArray("enum");
            if (enumSchema.enumValues() != null) {
                enumSchema.enumValues().forEach(values::add);
            }
            putDescription(node, enumSchema.description());
            return node;
        }
        if (element instanceof JsonNullSchema nullSchema) {
            node.put("type", "null");
            putDescription(node, nullSchema.description());
            return node;
        }
        if (element instanceof JsonReferenceSchema referenceSchema) {
            node.put("$ref", referenceSchema.reference());
            putDescription(node, referenceSchema.description());
            return node;
        }
        if (element instanceof JsonAnyOfSchema anyOfSchema) {
            ArrayNode anyOf = node.putArray("anyOf");
            if (anyOfSchema.anyOf() != null) {
                anyOfSchema.anyOf().forEach(schema -> anyOf.add(jsonSchemaElementToNode(schema)));
            }
            putDescription(node, anyOfSchema.description());
            return node;
        }

        node.put("type", "string");
        return node;
    }

    private void putDescription(ObjectNode node, String description) {
        if (description != null && !description.isBlank()) {
            node.put("description", description);
        }
    }

    private ChatResponse convertResponse(ChatRequest request, ChatResponseEnvelope response) {
        List<ToolExecutionRequest> toolRequests = toToolExecutionRequests(response.getToolCalls());
        AiMessage aiMessage = AiMessage.from(safeText(response.getContent()), toolRequests);

        ChatResponse.Builder builder = ChatResponse.builder()
                .aiMessage(aiMessage)
                .finishReason(mapFinishReason(response.getFinishReason()))
                .tokenUsage(toTokenUsage(response.getUsage()));

        JsonNode raw = response.getRawResponse();
        String modelName = resolveResponseModelName(request, raw);
        if (modelName != null && !modelName.isBlank()) {
            builder.modelName(modelName);
        }

        String id = resolveResponseId(raw);
        if (id != null && !id.isBlank()) {
            builder.id(id);
        }
        return builder.build();
    }

    private String resolveResponseModelName(ChatRequest request, JsonNode raw) {
        String modelName = request.modelName();
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

    private List<ToolExecutionRequest> toToolExecutionRequests(List<JsonNode> toolCalls) {
        List<ToolExecutionRequest> requests = new ArrayList<>();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return requests;
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
            requests.add(ToolExecutionRequest.builder()
                    .id(id == null || id.isBlank() ? newToolCallId() : id)
                    .name(name)
                    .arguments(arguments)
                    .build());
        }
        return requests;
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

    private FinishReason mapFinishReason(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return null;
        }
        String normalized = finishReason.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "stop", "completed", "end_turn", "stop_sequence" -> FinishReason.STOP;
            case "length", "max_tokens", "max_output_tokens" -> FinishReason.LENGTH;
            case "tool_calls", "function_call", "tool_use" -> FinishReason.TOOL_EXECUTION;
            case "content_filter", "safety", "blocked" -> FinishReason.CONTENT_FILTER;
            default -> FinishReason.OTHER;
        };
    }

    private TokenUsage toTokenUsage(JsonNode usage) {
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
        return new TokenUsage(input, output, total);
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String newToolCallId() {
        return "call_" + UUID.randomUUID().toString().replace("-", "");
    }
}
