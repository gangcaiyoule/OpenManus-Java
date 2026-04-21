package com.openmanus.aiframework.tool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Scans @AiTool methods and creates executable runtime tool registrations.
 */
public final class AiToolRegistry {

    private static final Gson GSON = new Gson();
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private AiToolRegistry() {
    }

    public static List<AiRegisteredTool> scan(Object objectWithTools) {
        Objects.requireNonNull(objectWithTools, "objectWithTools cannot be null");
        Map<String, AiRegisteredTool> tools = new LinkedHashMap<>();
        for (Method method : objectWithTools.getClass().getMethods()) {
            AiTool annotation = method.getAnnotation(AiTool.class);
            if (annotation == null) {
                continue;
            }
            AiRegisteredTool tool = toRegisteredTool(objectWithTools, method, annotation);
            AiRegisteredTool previous = tools.putIfAbsent(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate tool name detected: " + tool.name());
            }
        }
        return List.copyOf(tools.values());
    }

    public static List<AiToolSpec> toRuntimeToolSpecifications(Collection<AiRegisteredTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<AiToolSpec> specs = new ArrayList<>(tools.size());
        for (AiRegisteredTool tool : tools) {
            if (tool != null) {
                specs.add(tool.toRuntimeToolSpec());
            }
        }
        return List.copyOf(specs);
    }

    private static AiRegisteredTool toRegisteredTool(Object target, Method method, AiTool annotation) {
        String toolName = nonBlank(annotation.name(), method.getName());
        String description = annotation.value();
        ParameterBinding[] bindings = parameterBindings(method);
        AiAgentParameterSchema parameters = buildParametersSchema(bindings);

        AiToolExecutor executor = (request, memoryId) -> invoke(target, method, bindings, request, memoryId);
        return new AiRegisteredTool(toolName, description, parameters, executor);
    }

    private static ParameterBinding[] parameterBindings(Method method) {
        Parameter[] parameters = method.getParameters();
        ParameterBinding[] bindings = new ParameterBinding[parameters.length];
        String[] discoveredNames = discoverParameterNames(parameters);
        Set<String> exposedNames = new HashSet<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            AiParam annotation = parameter.getAnnotation(AiParam.class);
            String fallbackName = discoveredNames[i] == null || discoveredNames[i].isBlank()
                    ? parameter.getName()
                    : discoveredNames[i];
            String name = annotation == null
                    ? fallbackName
                    : nonBlank(annotation.name(), fallbackName);
            boolean exposed = parameter.getType() != AiToolExecutionRequest.class
                    && !"memoryId".equals(name);
            if (exposed && !exposedNames.add(name)) {
                throw new IllegalStateException(
                        "Duplicate tool parameter name detected: tool=" + method.getName() + ", parameter=" + name);
            }
            String description = annotation == null ? "" : annotation.value();
            boolean required = annotation == null || annotation.required();
            bindings[i] = new ParameterBinding(name, description, required, parameter.getType(), exposed);
        }
        return bindings;
    }

    private static String[] discoverParameterNames(Parameter[] parameters) {
        if (parameters.length == 0) {
            return new String[0];
        }
        Method method = (Method) parameters[0].getDeclaringExecutable();
        String[] discovered = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        if (discovered == null || discovered.length != parameters.length) {
            discovered = new String[parameters.length];
        }
        return discovered;
    }

    private static AiAgentParameterSchema buildParametersSchema(ParameterBinding[] bindings) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ArrayNode required = root.putArray("required");
        for (ParameterBinding binding : bindings) {
            if (!binding.exposed()) {
                continue;
            }
            ObjectNode property = properties.putObject(binding.name());
            property.put("type", jsonSchemaType(binding.type()));
            if (binding.description() != null && !binding.description().isBlank()) {
                property.put("description", binding.description());
            }
            if (binding.required()) {
                required.add(binding.name());
            }
        }
        return new AiAgentParameterSchema(root);
    }

    private static String jsonSchemaType(Class<?> type) {
        if (type == null) {
            return "string";
        }
        if (type == Object.class) {
            return "object";
        }
        if (type.isArray()) {
            return "array";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == byte.class
                || type == short.class
                || type == int.class
                || type == long.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class) {
            return "integer";
        }
        if (type == float.class
                || type == double.class
                || type == Float.class
                || type == Double.class) {
            return "number";
        }
        if (type.isEnum()) {
            return "string";
        }
        if (CharSequence.class.isAssignableFrom(type) || type == char.class || type == Character.class) {
            return "string";
        }
        if (Collection.class.isAssignableFrom(type)) {
            return "array";
        }
        if (Map.class.isAssignableFrom(type)
                || JsonElement.class.isAssignableFrom(type)
                || JsonObject.class.isAssignableFrom(type)) {
            return "object";
        }
        Package typePackage = type.getPackage();
        if (typePackage != null && typePackage.getName().startsWith("java.")) {
            return "string";
        }
        return "object";
    }

    private static String invoke(Object target,
                                 Method method,
                                 ParameterBinding[] bindings,
                                 AiToolExecutionRequest request,
                                 Object memoryId) {
        try {
            if (!method.canAccess(target)) {
                method.setAccessible(true);
            }
            JsonObject arguments = parseArguments(request.arguments());
            Object[] invokeArgs = new Object[bindings.length];
            for (int i = 0; i < bindings.length; i++) {
                ParameterBinding binding = bindings[i];
                invokeArgs[i] = resolveArgument(binding, arguments, request, memoryId);
            }
            Object result = method.invoke(target, invokeArgs);
            return result == null ? "" : String.valueOf(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException("Tool execution failed: " + cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Tool execution failed: " + e.getMessage(), e);
        }
    }

    private static Object resolveArgument(ParameterBinding binding,
                                          JsonObject arguments,
                                          AiToolExecutionRequest request,
                                          Object memoryId) {
        if (binding.type() == AiToolExecutionRequest.class) {
            return request;
        }
        if ("memoryId".equals(binding.name())) {
            if (binding.required() && memoryId == null) {
                throw new IllegalArgumentException(
                        "Missing required tool argument '" + binding.name() + "' for tool '" + request.name() + "'");
            }
            if (memoryId == null) {
                return defaultValue(binding.type());
            }
            if (binding.type() == Object.class) {
                return memoryId;
            }
            String memoryIdText = String.valueOf(memoryId);
            return convert(JsonParser.parseString(GSON.toJson(memoryIdText)), binding.type());
        }

        JsonElement value = arguments.get(binding.name());
        if (binding.required() && (value == null || value.isJsonNull())) {
            throw new IllegalArgumentException(
                    "Missing required tool argument '" + binding.name() + "' for tool '" + request.name() + "'");
        }
        return convert(value, binding.type());
    }

    private static Object convert(JsonElement value, Class<?> targetType) {
        if (targetType == String.class) {
            if (value == null || value.isJsonNull()) {
                return null;
            }
            return value.isJsonPrimitive() ? value.getAsString() : value.toString();
        }

        if (value == null || value.isJsonNull()) {
            return defaultValue(targetType);
        }

        if (targetType == int.class || targetType == Integer.class) {
            return value.getAsInt();
        }
        if (targetType == long.class || targetType == Long.class) {
            return value.getAsLong();
        }
        if (targetType == double.class || targetType == Double.class) {
            return value.getAsDouble();
        }
        if (targetType == float.class || targetType == Float.class) {
            return value.getAsFloat();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return value.getAsBoolean();
        }
        if (targetType == JsonElement.class) {
            return value;
        }
        if (targetType.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumValue = Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), value.getAsString());
            return enumValue;
        }
        return GSON.fromJson(value, targetType);
    }

    private static Object defaultValue(Class<?> targetType) {
        if (!targetType.isPrimitive()) {
            return null;
        }
        if (targetType == boolean.class) {
            return false;
        }
        if (targetType == char.class) {
            return '\0';
        }
        if (targetType == byte.class) {
            return (byte) 0;
        }
        if (targetType == short.class) {
            return (short) 0;
        }
        if (targetType == int.class) {
            return 0;
        }
        if (targetType == long.class) {
            return 0L;
        }
        if (targetType == float.class) {
            return 0f;
        }
        if (targetType == double.class) {
            return 0d;
        }
        return null;
    }

    private static JsonObject parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(arguments);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            JsonObject wrapped = new JsonObject();
            wrapped.addProperty("value", arguments);
            return wrapped;
        } catch (Exception ignored) {
            JsonObject wrapped = new JsonObject();
            wrapped.addProperty("value", arguments);
            return wrapped;
        }
    }

    private static String nonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }

    private record ParameterBinding(String name, String description, boolean required, Class<?> type, boolean exposed) {
    }
}
