package com.homework6.pipeline.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Single shared ObjectMapper configuration for all pipeline JSON I/O.
 *
 * Per research-notes.md (context7 query 1): classic jackson-databind (2.x) does not
 * auto-register java.time support the way the newer 3.x MapperBuilder does, so
 * JavaTimeModule must be registered explicitly, and WRITE_DATES_AS_TIMESTAMPS disabled
 * so OffsetDateTime fields serialize as ISO-8601 strings.
 */
public final class JsonMapper {

    private static final ObjectMapper INSTANCE = build();

    private JsonMapper() {
    }

    public static ObjectMapper instance() {
        return INSTANCE;
    }

    private static ObjectMapper build() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper;
    }
}
