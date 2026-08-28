package site.omagotchi.learningservice.space.application.port;

import java.util.Optional;

/**
 * 다른 Feature가 공간의 관리 주체 기수만 읽는 경계.
 *
 * <p>{@link SpaceNameQueryPort}·{@link SpaceAccessQueryPort}와 나눠 둔 이유는 앞의 둘이
 * 각자의 javadoc에서 밝힌 것과 같다. 이름 조회는 "이름만"이 계약이고, 이용 가능 여부
 * 판정은 {@code SpaceAccessView}가 <b>관리 주체 기수를 담지 않는 것</b>을 계약으로 못박아
 * 두었다. 소비처가 생겼다고 그 둘에 필드를 끼워 넣으면 원래 목적이 흐려진다.</p>
 */
public interface SpaceCohortQueryPort {

    /**
     * 삭제되지 않은 공간의 관리 주체 기수를 읽는다.
     *
     * <p><b>비어 있는 경우가 둘이고 소비처는 구분하지 않아도 된다.</b> 공간이 없거나
     * 소프트 삭제됐거나({@code deleted_at}), 기수가 배정되지 않은 공용 공간이거나
     * ({@code cohort_id}가 NULL) — 어느 쪽이든 "이 공간의 담당 기수를 말할 수 없다"는
     * 같은 결론이다.</p>
     */
    Optional<Long> findCohortId(Long spaceId);
}
