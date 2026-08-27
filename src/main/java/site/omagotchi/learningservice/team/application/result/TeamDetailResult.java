package site.omagotchi.learningservice.team.application.result;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 팀 상세와 팀원 목록 (GR-06, GR-15). 그 팀 소속자에게만 반환된다.
 *
 * <p>{@code memberCount}는 별도 카운트 쿼리가 아니라 {@code members.size()}다.
 * 목록과 숫자가 어긋나면 클라이언트가 "8명인데 7명만 보인다" 같은 상태를 만나므로,
 * 같은 조회 결과에서 파생시켜 항상 일치시킨다. 정원(GR-17) 판정에는 쓰지 않는다 —
 * 그건 락 안에서 다시 세야 한다.</p>
 *
 * <p>팀원은 마스터가 먼저, 그다음 가입 순으로 정렬되어 있다.</p>
 */
public record TeamDetailResult(
        Long teamId,
        Long cohortId,
        String name,
        OffsetDateTime createdAt,
        List<TeamMemberResult> members
) {
    /** {@code members}는 이미 정렬된(마스터 우선) 목록이어야 한다. 여기서 다시 정렬하지 않는다. */
    public TeamDetailResult {
        members = List.copyOf(members);
    }

    public int memberCount() {
        return members.size();
    }
}
