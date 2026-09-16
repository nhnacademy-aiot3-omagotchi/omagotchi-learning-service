package site.omagotchi.learningservice.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;

/** Security, JWT, profile, and REST Docs configuration shared by MVC documentation tests. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({
    SecurityConfig.class,
    JwtConfig.class,
    JwtAuthorityConfig.class,
    SecurityErrorResponseHandler.class,
    TestJwtKeyConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
public @interface LearningRestDocsTest {}
