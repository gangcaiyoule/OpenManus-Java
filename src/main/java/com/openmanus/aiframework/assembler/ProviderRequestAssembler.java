package com.openmanus.aiframework.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.aiframework.model.ChatRequestEnvelope;

public interface ProviderRequestAssembler {

    JsonNode assemble(ChatRequestEnvelope request, boolean stream);
}
