package site.omagotchi.learningservice.community.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CommunityAttachmentProperties.class)
public class CommunityAttachmentConfig {
}
