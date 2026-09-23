package io.github.elanthirian.uafields;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable parse. {@link #fields()} uses the public field names. */
public final class ParseResult {
    private final Map<String, Object> fields;

    ParseResult(Map<String, Object> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, Object> fields() {
        return fields;
    }

    public Object get(String field) {
        return fields.get(field);
    }

    @Override
    public String toString() {
        return JsonWriter.write(fields);
    }
}
