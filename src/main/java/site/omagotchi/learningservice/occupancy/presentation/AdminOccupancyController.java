package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.occupancy.application.AdminOccupancyQueryService;
import site.omagotchi.learningservice.occupancy.presentation.response.AdminActiveOccupancyResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/spaces/occupancies")
@RequiredArgsConstructor
public class AdminOccupancyController {

    private final AdminOccupancyQueryService adminOccupancyQueryService;

    @GetMapping
    public List<AdminActiveOccupancyResponse> getActiveOccupancies(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return adminOccupancyQueryService
                .getActiveOccupancies(AuthenticatedUser.from(jwt).userId()).stream()
                .map(AdminActiveOccupancyResponse::from)
                .toList();
    }
}
