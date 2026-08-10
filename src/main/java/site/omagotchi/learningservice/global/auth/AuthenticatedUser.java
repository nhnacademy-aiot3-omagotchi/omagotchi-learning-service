package site.omagotchi.learningservice.global.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        GlobalRole globalRole
) {

    public static AuthenticatedUser from(JwtAuthenticationToken authentication) {
        return from(authentication.getToken());
    }

    public static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                GlobalRole.valueOf(jwt.getClaimAsString("role"))
        );
    }

    public static AuthenticatedUser from(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return from(jwtAuthentication);
        }

        GlobalRole globalRole = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .filter(GlobalRole::isSupported)
                .map(GlobalRole::valueOf)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하는 전역 역할이 없습니다."
                ));

        return new AuthenticatedUser(
                UUID.fromString(authentication.getName()),
                globalRole
        );
    }
}
