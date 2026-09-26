package service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance, lightweight pure Java JSON Parser.
 * Zero external dependencies (no Gson / Jackson required).
 * Safely parses JSON strings into Maps, Lists, Strings, Numbers, and Booleans.
 */
public class JsonParser {

    private final String src;
    private int pos = 0;
    private final int len;

    public JsonParser(String src) {
        this.src = (src != null) ? src.trim() : "";
        this.len = this.src.length();
    }

    public static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return new JsonParser(json).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object obj = parse(json);
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseList(String json) {
        Object obj = parse(json);
        if (obj instanceof List) {
            return (List<Object>) obj;
        }
        return new ArrayList<>();
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= len) return null;

        char c = src.charAt(pos);
        if (c == '{') {
            return parseObjectInternal();
        } else if (c == '[') {
            return parseArrayInternal();
        } else if (c == '"' || c == '\'') {
            return parseStringInternal();
        } else if (c == 't' || c == 'T' || c == 'f' || c == 'F') {
            return parseBooleanInternal();
        } else if (c == 'n' || c == 'N') {
            return parseNullInternal();
        } else if (c == '-' || (c >= '0' && c <= '9')) {
            return parseNumberInternal();
        }
        return null;
    }

    private Map<String, Object> parseObjectInternal() {
        Map<String, Object> map = new HashMap<>();
        pos++; // Skip '{'
        skipWhitespace();

        while (pos < len && src.charAt(pos) != '}') {
            skipWhitespace();
            if (pos >= len || src.charAt(pos) == '}') break;

            String key = parseStringInternal();
            skipWhitespace();
            if (pos < len && src.charAt(pos) == ':') {
                pos++; // Skip ':'
            }
            skipWhitespace();
            Object value = parseValue();
            map.put(key, value);

            skipWhitespace();
            if (pos < len && src.charAt(pos) == ',') {
                pos++; // Skip ','
            }
            skipWhitespace();
        }
        if (pos < len && src.charAt(pos) == '}') {
            pos++; // Skip '}'
        }
        return map;
    }

    private List<Object> parseArrayInternal() {
        List<Object> list = new ArrayList<>();
        pos++; // Skip '['
        skipWhitespace();

        while (pos < len && src.charAt(pos) != ']') {
            skipWhitespace();
            if (pos >= len || src.charAt(pos) == ']') break;

            Object val = parseValue();
            list.add(val);

            skipWhitespace();
            if (pos < len && src.charAt(pos) == ',') {
                pos++; // Skip ','
            }
            skipWhitespace();
        }
        if (pos < len && src.charAt(pos) == ']') {
            pos++; // Skip ']'
        }
        return list;
    }

    private String parseStringInternal() {
        char quote = src.charAt(pos);
        if (quote != '"' && quote != '\'') return "";
        pos++; // Skip quote

        StringBuilder sb = new StringBuilder();
        while (pos < len) {
            char c = src.charAt(pos++);
            if (c == quote) {
                break;
            } else if (c == '\\' && pos < len) {
                char next = src.charAt(pos++);
                switch (next) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 <= len) {
                            String hex = src.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            } catch (Exception e) {
                                sb.append(next);
                            }
                        }
                        break;
                    default: sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object parseNumberInternal() {
        int start = pos;
        if (src.charAt(pos) == '-') pos++;
        while (pos < len && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
        }
        String numStr = src.substring(start, pos);
        try {
            if (numStr.contains(".")) {
                return Double.parseDouble(numStr);
            } else {
                long l = Long.parseLong(numStr);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private Boolean parseBooleanInternal() {
        if (src.regionMatches(true, pos, "true", 0, 4)) {
            pos += 4;
            return true;
        } else if (src.regionMatches(true, pos, "false", 0, 5)) {
            pos += 5;
            return false;
        }
        return false;
    }

    private Object parseNullInternal() {
        if (src.regionMatches(true, pos, "null", 0, 4)) {
            pos += 4;
        }
        return null;
    }

    private void skipWhitespace() {
        while (pos < len && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }
}
