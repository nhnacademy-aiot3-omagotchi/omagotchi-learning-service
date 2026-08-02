package site.omagotchi.learningservice.team.application;

/**
 * 마스터 위임·팀 해체·회원 삭제 훅을 담을 자리. <b>아직 비어 있다</b> (이슈 #8).
 *
 * <p>빈 클래스가 남아 있는 이유는 이 세 흐름이 하나의 불변식 — "팀당 MASTER 정확히 1명" —
 * 을 공유하기 때문이다. {@code TeamMemberService}에 섞으면 위임 순서와 자동 위임 기준이
 * 팀원 추가 로직 사이에 흩어져, 어디서 MASTER가 0명이 되는지 추적하기 어려워진다.</p>
 *
 * <p>구현할 것 (명세 06):</p>
 * <ul>
 *   <li>마스터 위임 (GR-12, GR-14, GR-20) — {@code team_members}를 id 오름차순으로 락한 뒤
 *       기존 MASTER 강등을 flush하고 나서 대상을 승격한다. 역순은
 *       {@code uq_team_members_one_master} 위반이고, 정렬하지 않은 락은 데드락이다.</li>
 *   <li>팀 해체 (GR-19) — {@code teams.deleted_at} 기록 + 팀원 전 행 물리 삭제,
 *       커밋 후 (구)팀원에게 비동기 통보.</li>
 *   <li>회원 삭제 훅의 팀 처리 (GR-16) — 소속 행 삭제, 대상이 MASTER였다면
 *       {@code joined_at} 최소(동률 시 id 최소)에게 자동 위임, 남은 사람이 없으면 팀 소프트 삭제.
 *       같은 이벤트가 두 번 와도 부작용이 없어야 한다.</li>
 * </ul>
 *
 * <p>필요한 조회는 {@code TeamMemberRepository}에 이미 준비돼 있다 —
 * {@code findAllByTeamIdForUpdate}, {@code findSuccessorCandidates},
 * {@code deleteByTeamId}, {@code countByTeamIdAndRole}. 에러 코드도
 * {@code MASTER_STATE_CONFLICT}, {@code CANNOT_DELEGATE_TO_SELF}가 미리 있다.
 * 지금 쓰이지 않는다고 지우면 안 된다.</p>
 *
 * <p>기수 종료 연동(CE-01, 이슈 #14)이 여기 로직을 재사용하므로, 팀 정리는
 * 외부에서 호출 가능한 메서드로 분리해 둘 것.</p>
 *
 * <p>훅의 진입점(이벤트 수신 vs API 콜백)은 인증 파트와 계약이 확정되기 전까지 열어 둔다.
 * 그때까지는 내부 메서드만 만들고 진입점은 나중에 붙인다.</p>
 */
public class TeamMasterService {
}
