package controller;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** JSON for setup fragments and responses. Escapes characters that can close an HTML script. */
public final class SetupJson {
    private SetupJson() {}

    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?>) {
            return ((Map<?, ?>) value).entrySet().stream()
                    .map(entry -> encode(entry.getKey().toString()) + ":" + encode(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?>) {
            return StreamSupport.stream(((Iterable<?>) value).spliterator(), false)
                    .map(SetupJson::encode).collect(Collectors.joining(",", "[", "]"));
        }
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toString().toCharArray()) {
            if (character == '"' || character == '\\') result.append('\\').append(character);
            else if (character < 32 || character == '<') result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.append('"').toString();
    }
}
