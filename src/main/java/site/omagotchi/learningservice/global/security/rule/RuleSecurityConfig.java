package site.omagotchi.learningservice.global.security.rule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.basic.ServiceCredentialAuthenticationProviderFactory;

// Rule 전용 임계치 기준 조회 API의 서비스 인증 경계
@Configuration(proxyBeanMethods = false)
public class RuleSecurityConfig {

    private static final String RULE_ROLE = "RULE";
    private static final String RULE_REALM = "omagotchi-learning-rule";
    private static final String INTERNAL_RULES_PATH = "/api/v1/internal/threshold-rules";

    @Bean
    @Order(1)
    SecurityFilterChain ruleSecurityFilterChain(
            HttpSecurity http,
            SecurityErrorResponseHandler errorHandler,
            ServiceCredentialAuthenticationProviderFactory providerFactory,
            RuleCredentialProperties properties
    ) {
        http
                .securityMatcher(INTERNAL_RULES_PATH + "/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(providerFactory.create(
                        properties.username(),
                        properties.password(),
                        RULE_ROLE
                ))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, INTERNAL_RULES_PATH)
                        .hasRole(RULE_ROLE)
                        .anyRequest().denyAll()
                )
                .httpBasic(httpBasic -> httpBasic
                        .authenticationEntryPoint(
                                errorHandler.basicAuthenticationEntryPoint(RULE_REALM)
                        )
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                errorHandler.basicAuthenticationEntryPoint(RULE_REALM)
                        )
                        .accessDeniedHandler(errorHandler.basicAccessDeniedHandler())
                );

        return http.build();
    }
}
