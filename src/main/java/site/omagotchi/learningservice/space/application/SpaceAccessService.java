package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.SpaceAccessQueryPort;
import site.omagotchi.learningservice.space.application.result.SpaceAccessView;

import java.util.Optional;

/**
 * 다른 Feature가 공간의 이용 가능 여부를 확인하는 공개 계약.
 *
 * <p>{@code occupancy}가 첫 소비처다 — 점유 시작이 "회의실인가"(MR-20)와 "활성인가"(RM-13)를,
 * 참여자 추가가 정원(MR-28)을 물어본다. 그쪽이 {@code spaces}를 직접 읽으면 공간의 상태
 * 판정 규칙이 두 곳에 복제되고, 활성 조건이 바뀔 때 한쪽만 고쳐진다.</p>
 *
 * <p>{@link SpaceCommandService}·{@link SpaceQueryService}와 나눠 둔 이유는 대상이 다르기
 * 때문이다. 저 둘은 관리 화면과 목록 화면이라는 <b>사람이 보는 흐름</b>을 담당하고 권한을
 * 판정하지만, 여기는 <b>다른 Feature가 판정 근거를 얻는</b> 읽기 전용 통로라 권한을 보지 않는다 —
 * 점유할 자격이 있는지는 점유가 스스로 판단한다.</p>
 *
 * <p>예외를 던지지 않고 {@link Optional}을 돌려주는 것도 같은 이유다. 같은 "공간 없음"이
 * 점유 시작에서는 404지만 다른 흐름에서는 다른 코드일 수 있어, 판단을 호출부에 남긴다
 * ({@code CohortMembershipQueryService}와 같은 규약).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceAccessService {

    private final SpaceAccessQueryPort spaceAccessQueryPort;

    /**
     * 락 없이 공간 상태를 읽는다. 락 밖 사전 검증에 쓴다.
     *
     * <p>뒤이어 {@link #lock(Long)}을 부를 계획이어도 안전하다 — 엔티티를 만들지 않으므로
     * 1차 캐시가 락 결과를 가리지 않는다.</p>
     */
    public Optional<SpaceAccessView> find(Long spaceId) {
        return spaceAccessQueryPort.find(spaceId);
    }

    /**
     * {@code spaces} 행을 배타 락으로 잡고 상태를 읽는다.
     *
     * <p><b>호출부의 트랜잭션에 참여한다.</b> 클래스 레벨 {@code readOnly = true}는 쓰기를 하지
     * 않는다는 선언일 뿐 새 트랜잭션을 열지 않으므로, 점유 시작이 연 트랜잭션 안에서 락이
     * 유지된다. 트랜잭션 없이 부르면 구현이 명시적으로 막는다.</p>
     *
     * <p>활성 여부는 락을 잡은 <b>뒤에</b> 확인해야 한다 — 자세한 이유는 Port javadoc 참고.</p>
     */
    public Optional<SpaceAccessView> lock(Long spaceId) {
        return spaceAccessQueryPort.lock(spaceId);
    }
}
