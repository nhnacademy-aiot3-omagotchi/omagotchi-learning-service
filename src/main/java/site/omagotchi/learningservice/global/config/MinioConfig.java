package site.omagotchi.learningservice.global.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class MinioConfig {

    @Bean
    @Primary
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * 기동 중 버킷 설정을 확인하는 전용 client다. 실제 파일 전송 client와 timeout을 공유하면
     * 큰 파일 업로드·다운로드까지 짧은 timeout으로 끊길 수 있다.
     */
    @Bean
    public MinioClient communityAttachmentBucketCheckMinioClient(MinioProperties properties) {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        client.setTimeout(
                TimeUnit.SECONDS.toMillis(2),
                TimeUnit.SECONDS.toMillis(2),
                TimeUnit.SECONDS.toMillis(5)
        );
        return client;
    }
}
