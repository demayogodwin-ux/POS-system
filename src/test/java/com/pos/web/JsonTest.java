package com.pos.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void readsAnOrderRequest() {
        Object parsed = Json.parse("{\"type\":\"DELIVERY\",\"lat\":10.69,\"lng\":null,\"items\":[{\"productId\":3,\"quantity\":2}],\"note\":\"Gate \\\"B\\\"\\n\\u00f1\"}");
        Map<?, ?> body = (Map<?, ?>)parsed;
        assertEquals("DELIVERY", body.get("type"));
        assertEquals(new BigDecimal("10.69"), body.get("lat"));
        assertEquals(null, body.get("lng"));
        assertEquals(new BigDecimal("2"), ((Map<?, ?>)((List<?>)body.get("items")).get(0)).get("quantity"));
        assertEquals("Gate \"B\"\n\u00f1", body.get("note"));
    }

    @Test
    void writesEscapedText() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Tom's \"best\" </script>");
        map.put("price", new BigDecimal("12.50"));
        map.put("tags", List.of(true, 3));
        assertEquals("{\"name\":\"Tom's \\\"best\\\" \\u003c/script>\",\"price\":12.50,\"tags\":[true,3]}", Json.write(map));
    }

    @Test
    void rejectsBrokenJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} extra"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[" .repeat(50)));
    }
}
