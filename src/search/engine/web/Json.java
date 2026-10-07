package search.engine.web;

import java.util.Map;

/** Small response encoder for the local API; no external JSON library needed. */
final class Json {
    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            for (var entry : map.entrySet()) {
                if (out.length() > 1) out.append(',');
                out.append(encode(entry.getKey().toString())).append(':').append(encode(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> items) {
            StringBuilder out = new StringBuilder("[");
            for (Object item : items) {
                if (out.length() > 1) out.append(',');
                out.append(encode(item));
            }
            return out.append(']').toString();
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
