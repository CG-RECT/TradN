package com.tradn.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/** 统一配置接口 JSON 序列化规则。 */
public class JacksonConfig {

    /**
     * 将 64 位整数序列化为字符串。
     *
     * <p>MyBatis-Plus 的雪花主键通常超过 JavaScript 安全整数上限。如果按 JSON 数字返回，浏览器会发生精度丢失，
     * 进而导致新建记录后使用错误主键查询详情。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
