package com.tradn.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** 验证浏览器端无法安全表示的 64 位主键按字符串输出。 */
class JacksonConfigTest {

    @Test
    void shouldSerializeLongAsString() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().longAsStringCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        assertEquals("\"9007199254740993\"", mapper.writeValueAsString(9007199254740993L));
    }
}
