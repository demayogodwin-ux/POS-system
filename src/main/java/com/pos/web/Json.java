package com.pos.web;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Just enough JSON for the phone app API, so the POS needs no extra library.
 * Objects are Maps, arrays are Lists, numbers are BigDecimals.
 */
public final class Json {
    private static final int MAX_DEPTH = 20;
    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            quote(out, s);
        } else if (value instanceof BigDecimal d) {
            out.append(d.toPlainString());
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Enum<?> e) {
            quote(out, e.name());
        } else if (value instanceof TemporalAccessor) {
            quote(out, value.toString());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;

            for (Entry<String, Object> entry : ((Map<String, Object>)map).entrySet()) {
                if (!first) {
                    out.append(',');
                }

                first = false;
                quote(out, entry.getKey());
                out.append(':');
                write(out, entry.getValue());
            }

            out.append('}');
        } else if (value instanceof Iterable<?> list) {
            out.append('[');
            boolean first = true;

            for (Object item : list) {
                if (!first) {
                    out.append(',');
                }

                first = false;
                write(out, item);
            }

            out.append(']');
        } else {
            throw new IllegalArgumentException("Cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    private static void quote(StringBuilder out, String s) {
        out.append('"');

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                // Escaping < keeps "</script>" harmless if a value ever lands inside HTML.
                case '<' -> out.append("\\u003c");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int)c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        out.append('"');
    }

    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipSpace();
        Object value = parser.value(0);
        parser.skipSpace();
        if (parser.pos != text.length()) {
            throw parser.error("Unexpected text after the JSON value");
        } else {
            return value;
        }
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw this.error("JSON is nested too deeply");
        } else if (this.pos >= this.text.length()) {
            throw this.error("Unexpected end of JSON");
        } else {
            char c = this.text.charAt(this.pos);
            if (c == '{') {
                return this.object(depth);
            } else if (c == '[') {
                return this.array(depth);
            } else if (c == '"') {
                return this.string();
            } else if (this.text.startsWith("true", this.pos)) {
                this.pos += 4;
                return Boolean.TRUE;
            } else if (this.text.startsWith("false", this.pos)) {
                this.pos += 5;
                return Boolean.FALSE;
            } else if (this.text.startsWith("null", this.pos)) {
                this.pos += 4;
                return null;
            } else {
                return this.number();
            }
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        this.pos++;
        this.skipSpace();
        if (this.peek() == '}') {
            this.pos++;
            return map;
        } else {
            while (true) {
                this.skipSpace();
                if (this.peek() != '"') {
                    throw this.error("Expected a property name");
                }

                String key = this.string();
                this.skipSpace();
                this.expect(':');
                this.skipSpace();
                map.put(key, this.value(depth + 1));
                this.skipSpace();
                if (this.peek() == ',') {
                    this.pos++;
                } else {
                    this.expect('}');
                    return map;
                }
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        this.pos++;
        this.skipSpace();
        if (this.peek() == ']') {
            this.pos++;
            return list;
        } else {
            while (true) {
                this.skipSpace();
                list.add(this.value(depth + 1));
                this.skipSpace();
                if (this.peek() == ',') {
                    this.pos++;
                } else {
                    this.expect(']');
                    return list;
                }
            }
        }
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        this.pos++;

        while (this.pos < this.text.length()) {
            char c = this.text.charAt(this.pos++);
            if (c == '"') {
                return out.toString();
            }

            if (c != '\\') {
                out.append(c);
            } else {
                if (this.pos >= this.text.length()) {
                    break;
                }

                char e = this.text.charAt(this.pos++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (this.pos + 4 > this.text.length()) {
                            throw this.error("Bad \\u escape");
                        }

                        try {
                            out.append((char)Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw this.error("Bad \\u escape");
                        }

                        this.pos += 4;
                    }
                    default -> throw this.error("Bad escape \\" + e);
                }
            }
        }

        throw this.error("Unterminated string");
    }

    private BigDecimal number() {
        int start = this.pos;

        while (this.pos < this.text.length() && "+-0123456789.eE".indexOf(this.text.charAt(this.pos)) >= 0) {
            this.pos++;
        }

        try {
            return new BigDecimal(this.text.substring(start, this.pos));
        } catch (NumberFormatException e) {
            this.pos = start;
            throw this.error("Unexpected character");
        }
    }

    private char peek() {
        return this.pos < this.text.length() ? this.text.charAt(this.pos) : '\0';
    }

    private void expect(char c) {
        if (this.peek() != c) {
            throw this.error("Expected '" + c + "'");
        } else {
            this.pos++;
        }
    }

    private void skipSpace() {
        while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
            this.pos++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + this.pos);
    }
}
