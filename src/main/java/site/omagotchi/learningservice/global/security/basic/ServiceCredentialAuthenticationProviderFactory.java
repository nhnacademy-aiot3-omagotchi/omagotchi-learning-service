package site.omagotchi.learningservice.global.security.basic;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;

/**
 * 호출 관계별 단일 HTTP Basic Credential 검증 Provider 생성.
 */
@Component
@RequiredArgsConstructor
public class ServiceCredentialAuthenticationProviderFactory {

    private final PasswordEncoder passwordEncoder;

    public AuthenticationProvider create(
            String username,
            String password,
            String role
    ) {
        UserDetails service = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .roles(role)
                .build();

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(new InMemoryUserDetailsManager(service));
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
