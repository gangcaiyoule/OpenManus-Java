package com.openmanus.aiframework.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.assembler.ProviderRequestAssembler;
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

            ChatResponseEnvelope done = ChatResponseEnvelope.builder()
                    .providerType(providerConfig.getProviderType())
                    .content(delta.toString())
                    .toolCalls(toolCalls)
                    .finishReason(latestFinishReason[0])
                    .usage(latestUsage[0])
                    .rawResponse(latestRawChunk[0])
                    .build();
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

    protected abstract void enrichHeaders(Map<String, String> headers);

    protected abstract String buildChatUrl(ChatRequestEnvelope request);

    protected abstract String buildStreamUrl(ChatRequestEnvelope request);
}
