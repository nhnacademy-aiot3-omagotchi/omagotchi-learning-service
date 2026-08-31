package site.omagotchi.learningservice.global.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityErrorResponseHandler errorHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) {
        http
                // Access Token은 Bearer Header 사용
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize
                        // 오류 처리 재디스패치가 인증 검사에 다시 막히지 않도록 허용
                        .dispatcherTypeMatchers(DispatcherType.ERROR
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/webhooks/telegram"
                        ).permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/ws",
                                "/ws/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/spaces"
                        ).permitAll()
                        .requestMatchers("/simulator/**")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cohorts",
                                "/api/v1/cohorts/*/managers"
                        ).hasRole("SYSTEM_ADMIN")
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/cohorts/*/status"
                        ).hasRole("SYSTEM_ADMIN")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cohorts/admin-summary"
                        ).hasRole("SYSTEM_ADMIN")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/cohorts/*"
                        ).hasRole("SYSTEM_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                );

        return http.build();
    }
}
