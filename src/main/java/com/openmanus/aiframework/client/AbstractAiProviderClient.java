package com.openmanus.aiframework.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.assembler.ProviderRequestAssembler;
import com.openmanus.aiframework.exception.AiFrameworkException;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatRequestOptions;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderConfig;
import com.openmanus.aiframework.model.ProviderStreamChunk;
import com.openmanus.aiframework.parser.ProviderResponseParser;
import com.openmanus.aiframework.transport.HttpTransport;
import com.openmanus.aiframework.transport.SseTransport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractAiProviderClient implements AiProviderClient {

    protected final ProviderConfig providerConfig;
    protected final ProviderRequestAssembler assembler;
    protected final ProviderResponseParser parser;
    protected final HttpTransport httpTransport;
    protected final SseTransport sseTransport;
    protected final ObjectMapper objectMapper;

    protected AbstractAiProviderClient(ProviderConfig providerConfig,
                                       ProviderRequestAssembler assembler,
                                       ProviderResponseParser parser,
                                       HttpTransport httpTransport,
                                       SseTransport sseTransport,
                                       ObjectMapper objectMapper) {
        this.providerConfig = providerConfig;
        this.assembler = assembler;
        this.parser = parser;
        this.httpTransport = httpTransport;
        this.sseTransport = sseTransport;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponseEnvelope chat(ChatRequestEnvelope request) {
        JsonNode payload = assembler.assemble(mergeDefaults(request), false);
        JsonNode response = httpTransport.postJson(
                buildChatUrl(request),
                payload,
                buildHeaders(),
                resolveTimeout(request),
                resolveMaxRetries(request)
        );
        if (isSseEnvelope(response)) {
            return parseSseChatResponse(response.path("events"));
        }
        return parser.parse(response);
    }

    @Override
    public void streamChat(ChatRequestEnvelope request, StreamListener listener) {
        ChatRequestEnvelope merged = mergeDefaults(request);
        JsonNode payload = assembler.assemble(merged, true);
        StringBuilder delta = new StringBuilder();
        List<JsonNode> toolCalls = new ArrayList<>();
        JsonNode[] latestUsage = new JsonNode[1];
        String[] latestFinishReason = new String[1];
        JsonNode[] latestRawChunk = new JsonNode[1];

        try {
            sseTransport.postSse(
                    buildStreamUrl(merged),
                    payload,
                    buildHeaders(),
                    resolveTimeout(merged),
                    resolveMaxRetries(merged),
                    (eventType, chunk) -> {
                        ProviderStreamChunk parsed = parser.parseStreamChunk(eventType, chunk);
                        latestRawChunk[0] = parsed.getRawChunk();
                        if (parsed.getDeltaText() != null && !parsed.getDeltaText().isBlank()) {
                            delta.append(parsed.getDeltaText());
                            listener.onDelta(parsed.getDeltaText());
                        }
                        if (parsed.getFinishReason() != null && !parsed.getFinishReason().isBlank()) {
                            latestFinishReason[0] = parsed.getFinishReason();
                        }
                        if (parsed.getUsage() != null && !parsed.getUsage().isNull() && !parsed.getUsage().isMissingNode()) {
                            latestUsage[0] = parsed.getUsage();
                        }
                        for (JsonNode toolCall : parsed.getToolCalls()) {
                            toolCalls.add(toolCall);
                            listener.onToolCall(toolCall.toString());
                        }
                        if (parsed.isCompleted()) {
                            return false;
                        }
                        return true;
                    }
            );

            ChatResponseEnvelope done = buildSseResponse(
                    delta,
                    toolCalls,
                    latestFinishReason[0],
                    latestUsage[0],
                    latestRawChunk[0]
            );
            listener.onComplete(done);
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    protected ChatRequestEnvelope mergeDefaults(ChatRequestEnvelope request) {
        String model = request.getModel();
        if (model == null || model.isBlank()) {
            model = providerConfig.getModel();
        }

        ChatRequestOptions options = request.getRequestOptions();
        if (options == null) {
            options = ChatRequestOptions.builder()
                    .timeoutSeconds(providerConfig.getTimeoutSeconds())
                    .maxRetries(providerConfig.getMaxRetries())
                    .build();
        }

        return ChatRequestEnvelope.builder()
                .providerType(request.getProviderType())
                .model(model)
                .messages(request.getMessages())
                .providerPayload(request.getProviderPayload())
                .requestOptions(options)
                .build();
    }

    protected int resolveTimeout(ChatRequestEnvelope request) {
        if (request.getRequestOptions() != null && request.getRequestOptions().getTimeoutSeconds() != null) {
            return request.getRequestOptions().getTimeoutSeconds();
        }
        return providerConfig.getTimeoutSeconds() == null ? 120 : providerConfig.getTimeoutSeconds();
    }

    protected int resolveMaxRetries(ChatRequestEnvelope request) {
        if (request.getRequestOptions() != null && request.getRequestOptions().getMaxRetries() != null) {
            return request.getRequestOptions().getMaxRetries();
        }
        return providerConfig.getMaxRetries() == null ? 1 : providerConfig.getMaxRetries();
    }

    protected Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        enrichHeaders(headers);
        return headers;
    }

    private boolean isSseEnvelope(JsonNode response) {
        return response != null
                && "sse".equals(response.path("_transport_format").asText(null))
                && response.path("events").isArray();
    }

    private ChatResponseEnvelope parseSseChatResponse(JsonNode events) {
        StringBuilder delta = new StringBuilder();
        List<JsonNode> toolCalls = new ArrayList<>();
        JsonNode latestUsage = null;
        String latestFinishReason = null;
        JsonNode latestRawChunk = null;

        for (JsonNode event : events) {
            String eventType = event.path("eventType").asText("message");
            JsonNode data = event.has("data") ? event.get("data") : null;
            if (data != null && data.path("error").isObject() && !data.path("error").isEmpty()) {
                throw new AiFrameworkException("Provider returned error payload: " + data.path("error"));
            }
            ProviderStreamChunk parsed = parser.parseStreamChunk(eventType, data);
            latestRawChunk = parsed.getRawChunk() != null ? parsed.getRawChunk() : latestRawChunk;
            if (parsed.getDeltaText() != null && !parsed.getDeltaText().isBlank()) {
                delta.append(parsed.getDeltaText());
            }
            if (parsed.getFinishReason() != null && !parsed.getFinishReason().isBlank()) {
                latestFinishReason = parsed.getFinishReason();
            }
            if (parsed.getUsage() != null && !parsed.getUsage().isNull() && !parsed.getUsage().isMissingNode()) {
                latestUsage = parsed.getUsage();
            } else if (data != null && data.path("usage").isObject()) {
                latestUsage = data.path("usage");
            }
            toolCalls.addAll(parsed.getToolCalls());
            if (parsed.isCompleted()) {
                break;
            }
        }

        return buildSseResponse(delta, toolCalls, latestFinishReason, latestUsage, latestRawChunk);
    }

    private ChatResponseEnvelope buildSseResponse(StringBuilder delta,
                                                  List<JsonNode> toolCalls,
                                                  String finishReason,
                                                  JsonNode usage,
                                                  JsonNode rawChunk) {
        if (delta.isEmpty() && toolCalls.isEmpty() && finishReason == null) {
            throw new AiFrameworkException("Provider returned empty SSE response without content or finish reason");
        }
        return ChatResponseEnvelope.builder()
                .providerType(providerConfig.getProviderType())
                .content(delta.toString())
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .usage(usage)
                .rawResponse(rawChunk)
                .build();
    }

    protected abstract void enrichHeaders(Map<String, String> headers);

    protected abstract String buildChatUrl(ChatRequestEnvelope request);

    protected abstract String buildStreamUrl(ChatRequestEnvelope request);
}
