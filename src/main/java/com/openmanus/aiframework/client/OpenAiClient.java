package com.openmanus.aiframework.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.assembler.ProviderRequestAssembler;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ProviderConfig;
import com.openmanus.aiframework.parser.ProviderResponseParser;
import com.openmanus.aiframework.transport.HttpTransport;
import com.openmanus.aiframework.transport.SseTransport;

import java.util.Map;

public class OpenAiClient extends AbstractAiProviderClient {

    public OpenAiClient(ProviderConfig providerConfig,
                        ProviderRequestAssembler assembler,
                        ProviderResponseParser parser,
                        HttpTransport httpTransport,
                        SseTransport sseTransport,
                        ObjectMapper objectMapper) {
        super(providerConfig, assembler, parser, httpTransport, sseTransport, objectMapper);
    }

    @Override
    protected void enrichHeaders(Map<String, String> headers) {
        headers.put("Authorization", "Bearer " + providerConfig.getApiKey());
    }

    @Override
    protected String buildChatUrl(ChatRequestEnvelope request) {
        return providerConfig.getBaseUrl().replaceAll("/$", "") + "/chat/completions";
    }

    @Override
    protected String buildStreamUrl(ChatRequestEnvelope request) {
        return buildChatUrl(request);
    }
}
