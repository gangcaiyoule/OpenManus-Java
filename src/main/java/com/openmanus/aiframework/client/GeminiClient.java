package com.openmanus.aiframework.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.assembler.ProviderRequestAssembler;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ProviderConfig;
import com.openmanus.aiframework.parser.ProviderResponseParser;
import com.openmanus.aiframework.transport.HttpTransport;
import com.openmanus.aiframework.transport.SseTransport;

import java.util.Map;

public class GeminiClient extends AbstractAiProviderClient {

    public GeminiClient(ProviderConfig providerConfig,
                        ProviderRequestAssembler assembler,
                        ProviderResponseParser parser,
                        HttpTransport httpTransport,
                        SseTransport sseTransport,
                        ObjectMapper objectMapper) {
        super(providerConfig, assembler, parser, httpTransport, sseTransport, objectMapper);
    }

    @Override
    protected void enrichHeaders(Map<String, String> headers) {
        // Gemini key as query param by default; no required auth header.
    }

    @Override
    protected String buildChatUrl(ChatRequestEnvelope request) {
        String base = providerConfig.getBaseUrl().replaceAll("/$", "");
        String model = request.getModel() == null || request.getModel().isBlank() ? providerConfig.getModel() : request.getModel();
        return base + "/v1beta/models/" + model + ":generateContent?key=" + providerConfig.getApiKey();
    }

    @Override
    protected String buildStreamUrl(ChatRequestEnvelope request) {
        String base = providerConfig.getBaseUrl().replaceAll("/$", "");
        String model = request.getModel() == null || request.getModel().isBlank() ? providerConfig.getModel() : request.getModel();
        return base + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + providerConfig.getApiKey();
    }
}
