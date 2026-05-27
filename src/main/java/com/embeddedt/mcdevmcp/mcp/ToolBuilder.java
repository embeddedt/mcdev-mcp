package com.embeddedt.mcdevmcp.mcp;

import com.embeddedt.mcdevmcp.external.ExternalToolException;
import com.embeddedt.mcdevmcp.jsonrpc.ToolCallResult;
import com.embeddedt.mcdevmcp.jsonrpc.ToolDefinition;
import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonValue;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ToolBuilder {

    /**
     * Build a JSON Schema (as a ZSON map) for a Java record type using reflection.
     */
    private static <T> Map<String, ZsonValue> buildSchema(Class<T> paramClass) {
        if (!paramClass.isRecord()) {
            throw new IllegalArgumentException("Parameter class must be a record: " + paramClass.getName());
        }

        RecordComponent[] components = paramClass.getRecordComponents();
        Map<String, ZsonValue> properties = Zson.object();
        List<Object> required = new ArrayList<>();

        for (RecordComponent comp : components) {
            properties.put(comp.getName(), new ZsonValue(typeSchema(comp.getType())));
            required.add(comp.getName());
        }

        return Zson.object(
                Zson.entry("type", "object"),
                Zson.entry("properties", properties),
                Zson.entry("required", required)
        );
    }

    /** Returns a ZSON map describing the JSON Schema type for a given Java class. */
    private static Map<String, ZsonValue> typeSchema(Class<?> type) {
        if (type == String.class) {
            return Zson.object(Zson.entry("type", "string"));
        } else if (type == boolean.class || type == Boolean.class) {
            return Zson.object(Zson.entry("type", "boolean"));
        } else if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return Zson.object(Zson.entry("type", "number"));
        } else if (type.isEnum()) {
            List<Object> enumValues = new ArrayList<>();
            for (Object c : type.getEnumConstants()) {
                enumValues.add(((Enum<?>) c).name());
            }
            return Zson.object(
                    Zson.entry("type", "string"),
                    Zson.entry("enum", enumValues)
            );
        } else if (type.isRecord()) {
            return buildSchema(type);
        } else {
            return Zson.object(Zson.entry("type", "object"));
        }
    }

    /**
     * Deserialize a ZSON arguments map into a record instance of {@code paramClass}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T deserialize(Map<String, ZsonValue> args, Class<T> paramClass) {
        RecordComponent[] components = paramClass.getRecordComponents();
        Object[] values = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent comp = components[i];
            ZsonValue entry = args.get(comp.getName());
            Object raw = entry != null ? entry.value : null;

            Class<?> targetType = comp.getType();
            if (targetType.isEnum() && raw instanceof String s) {
                raw = Enum.valueOf((Class<Enum>) targetType, s);
            }
            values[i] = raw;
        }

        Class<?>[] paramTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        try {
            return paramClass.getDeclaredConstructor(paramTypes).newInstance(values);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + paramClass.getSimpleName(), e);
        }
    }

    public static <T> ToolDefinition tool(String name, String description,
                                          Class<T> paramClass,
                                          Function<T, CompletableFuture<ToolCallResult>> toolHandler) {
        Map<String, ZsonValue> schema = buildSchema(paramClass);

        Function<Map<String, ZsonValue>, CompletableFuture<ToolCallResult>> handler = args -> {
            try {
                T params = deserialize(args, paramClass);
                return toolHandler.apply(params).exceptionally(error -> {
                    Throwable cause = (error instanceof java.util.concurrent.CompletionException && error.getCause() != null)
                            ? error.getCause() : error;
                    if (cause instanceof ExternalToolException externalToolException) {
                        return ToolCallResult.error(
                                "# standard output\n\n" + externalToolException.getStandardOutput()
                                + "# standard error\n\n" + externalToolException.getStandardError());
                    } else {
                        return ToolCallResult.error("Tool call failed: " + cause);
                    }
                });
            } catch (Exception e) {
                return CompletableFuture.completedFuture(ToolCallResult.error("Tool call failed: " + e));
            }
        };

        return new ToolDefinition(name, description, schema, handler);
    }
}
