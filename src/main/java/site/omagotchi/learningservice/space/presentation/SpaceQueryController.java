package site.omagotchi.learningservice.space.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.space.application.query.SpaceQueryService;
import site.omagotchi.learningservice.space.presentation.response.SpaceListResponse;

import java.util.List;

/**
 * 공간 조회 API Controller.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/spaces")
public class SpaceQueryController {

    private final SpaceQueryService spaceQueryService;

    /**
     * 삭제되지 않은 전체 공간과 현재 사용 상태를 조회한다.
     *
     * GET /api/spaces
     */
    @GetMapping
    public ResponseEntity<List<SpaceListResponse>> getSpaceList() {
        List<SpaceListResponse> response =
                spaceQueryService.getSpaceList()
                        .stream()
                        .map(SpaceListResponse::from)
                        .toList();

        return ResponseEntity.ok(response);
    }
}