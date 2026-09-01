package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.SpaceCohortQueryPort;

import java.util.Objects;
import java.util.Optional;

/**
 * 공간을 참조하는 <b>쓰기</b> 경로가 공간 행을 잠그고 기수를 확인하는 경계.
 *
 * <p>{@link SpaceCohortQueryService}와 나눈 이유는 트랜잭션 성격이 다르기 때문이다. 저쪽은
 * {@code readOnly = true}라 PostgreSQL이 {@code SELECT ... FOR UPDATE}를 거부한다. 지금은
 * 호출자의 쓰기 트랜잭션에 합류해 우연히 동작하겠지만, 조회 경로에서 불리는 순간 런타임에
 * 터진다 — 컴파일도 단위 테스트도 잡지 못한다. 그래서 아예 분리했다.</p>
 *
 * <p><b>전파를 {@code MANDATORY}로 둔 것이 의도다.</b> 트랜잭션 없이 부르면 즉시 실패해서
 * 잘못된 사용이 숨지 않는다. 잠금은 호출자의 트랜잭션이 끝날 때까지 유지되어야 의미가 있고,
 * 여기서 새 트랜잭션을 열면 반환하는 순간 풀려 아무것도 지키지 못한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SpaceCohortWriteGuard {

    private final SpaceCohortQueryPort spaceCohortQueryPort;

    /**
     * 공간 행을 잠근 뒤 관리 주체 기수를 읽는다.
     *
     * <p>삭제와 직렬화된다 — 공간 삭제도 같은 행을 잠근 뒤 센서 수를 세므로, 둘 중 하나는
     * 반드시 상대의 결과를 보고 진행한다.</p>
     */
    public Optional<Long> findCohortIdForUpdate(Long spaceId) {
        if (Objects.isNull(spaceId)) {
            return Optional.empty();
        }
        return spaceCohortQueryPort.findCohortIdByIdForUpdate(spaceId);
    }
}
