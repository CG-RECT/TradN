package com.tradn.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tradn.minio")
/** MinIO 客户端及文件存储连接参数配置。 */
public class MinioConfig {
    /** MinIO 服务访问地址。 */
    private String endpoint;

    /** MinIO 访问密钥。 */
    private String accessKey;

    /** MinIO 私密密钥。 */
    private String secretKey;

    /** 系统默认使用的存储桶名称。 */
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }
}
