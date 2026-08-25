package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.SpaceNameQueryPort;

import java.util.Optional;

/**
 * 다른 Feature가 공간 이름을 읽는 공개 계약.
 *
 * <p>공실 알림·만료 임박 알림 문구가 첫 소비처다. 사람이 읽는 문구에 공간 ID를 그대로
 * 노출하면 무슨 방인지 알아볼 수 없어 이름이 필요하다.</p>
 *
 * <p>{@link SpaceAccessService}와 나눠 둔 이유는 {@link site.omagotchi.learningservice.space.application.port.SpaceNameQueryPort}
 * javadoc 참고 — 이용 가능 여부 판정과 표시용 이름 조회는 목적이 다르다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceNameQueryService {

    private final SpaceNameQueryPort spaceNameQueryPort;

    public Optional<String> findName(Long spaceId) {
        return spaceNameQueryPort.findName(spaceId);
    }
}
