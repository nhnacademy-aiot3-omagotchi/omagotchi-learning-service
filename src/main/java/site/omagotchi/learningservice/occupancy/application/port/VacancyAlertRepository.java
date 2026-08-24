package site.omagotchi.learningservice.occupancy.application.port;

import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code vacancy_alerts} Persistence 경계.
 *
 * <p>취소·정리가 {@code left_at} 기록이 아니라 <b>물리 삭제</b>인 것이
 * {@link OccupancyParticipantRepository}와의 차이다. 신청은 구간이 아니라 일회성
 * 의사표시라 "취소한 신청"의 이력을 남길 이유가 없다.</p>
 *
 */
public interface VacancyAlertRepository {

    /**
     * 신청 행을 저장하고 즉시 flush한다.
     *
     * <p>flush가 계약의 일부다. 지연시키면 유니크 위반이 Transaction 종료 시점에 터져
     * 아래의 변환을 거치지 못한다.</p>
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         {@code uq_vacancy_alerts_waiting} 위반 시 {@code OCCUPANCY_ALERT_ALREADY_REQUESTED}(409).
     *         선검사가 있어도 동시 요청은 여기서만 막힌다
     */
    VacancyAlert save(VacancyAlert alert);

    /**
     * 이 멤버십들이 신청한 대기 중 목록. 오래된 신청이 먼저다.
     *
     * @param cohortMembershipIds 비어 있으면 빈 결과
     */
    List<VacancyAlert> findWaitingByMembershipIds(Collection<Long> cohortMembershipIds);

    /**
     * 이 회의실의 대기 중 신청 (MR-03 발송 대상).
     *
     * <p>엔티티가 아니라 값으로 돌려주는 것이 중요하다. 여기서 엔티티를 읽으면 1차
     * 캐시에 올라가, 건별로 다시 잠그는 {@link #lockWaitingById}가 {@code FOR UPDATE}를
     * 실행하고도 잠금 이전 스냅샷을 보게 된다 —
     * {@code RoomOccupancyRepository.findActiveSummaryBySpaceId}와 같은 전제다.</p>
     *
     * <p>멤버십까지 함께 담는 이유는 호출부가 수신자를 <b>한 번에</b> 조회하기 위해서다.
     * 발송 건별로 되물으면 {@code CohortMembershipQueryService.findUserIds}가 계약으로
     * 못박은 배치 성질이 깨져, 기수 모듈이 분리될 때 그대로 N+1 원격 호출이 된다.</p>
     *
     * @return 오래된 신청이 먼저. 비어 있으면 빈 목록
     */
    List<WaitingAlert> findWaitingBySpaceId(Long spaceId);

    /** 발송 후보 한 건. 수신자를 찾으려면 멤버십이 필요하다 ({@code vacancy_alerts}에 계정이 없다). */
    record WaitingAlert(Long alertId, Long cohortMembershipId) {
    }

    /**
     * 발송할 신청 한 건을 잠그고 아직 대기 중인지 재확인한다 (MR-03).
     *
     * <p><b>발송 경로에는 잠금이 필요하다.</b> "읽고 → 외부 발송 → 소진 기록"이 한 덩어리라,
     * 잠그지 않으면 여러 Instance가 같은 공실에 반응해 한 신청에 알림이 두 번 나간다.
     * 발송 전에 미리 소진시켜 잠금을 피할 수도 없다 — 그러면 발송 실패가 곧 신청 유실이다.</p>
     *
     * <p>취소({@link #deleteWaiting})가 잠금 대신 조건부 삭제를 쓰는 것과 대비된다.
     * 저쪽은 외부 호출을 끼고 있지 않아 한 문장으로 끝난다.</p>
     *
     * @return 그 사이 취소·소진됐으면 빈 결과. 호출부는 이것을 "대상 없음"으로 다룬다
     */
    Optional<VacancyAlert> lockWaitingById(Long alertId);

    /**
     * 대기 중인 <b>내</b> 신청을 지운다 (MR-17). 구간 모델이 아니므로 행을 지운다.
     *
     * <p><b>조건을 삭제문에 실어 잠금을 대신한다.</b> 먼저 읽고 지우면 그 사이 발송이
     * 끝났을 때 {@code WHERE id = ?}가 바뀐 상태를 보지 못해 <b>이미 발송된 신청의 이력을
     * 지운다.</b> 조건을 함께 걸면 삭제가 발송 커밋을 기다린 뒤 조건을 다시 평가해
     * 0행이 된다 — {@code RoomOccupancyRepository.expire}와 같은 방식이다.</p>
     *
     * <p>소유 검사까지 같은 문장에 넣는다. 세 실패(없음·이미 발송·남의 것)를 모두 같은
     * 404로 다루기로 했으므로 구분해 읽을 이유가 없고, 읽기와 삭제 사이의 틈도 사라진다.</p>
     *
     * @param cohortMembershipIds 요청자의 활성 소속. 비어 있으면 삭제하지 않는다
     * @return 실제로 지웠으면 {@code true}. {@code false}는 호출부가 404로 옮긴다
     */
    boolean deleteWaiting(Long alertId, Collection<Long> cohortMembershipIds);

    /**
     * 이 회의실의 대기 중 신청을 전부 지우고, <b>실제로 지운 행의 멤버십</b>을 돌려준다
     * (MR-21 강제 종료, RM-15 비활성화 정리).
     *
     * <p><b>삭제와 수신자 확보가 한 문장인 것이 계약이다</b> ({@code DELETE ... RETURNING}).
     * 먼저 읽고 나중에 지우면 그 사이 공실 발송이 행을 소진시킬 수 있다 — 삭제는
     * {@code notified_at IS NULL} 조건으로 그 행을 건너뛰지만, 미리 읽어 둔 수신자 목록에는
     * 남아서 <b>방금 공실 알림을 받은 사람에게 "신청이 취소됐다"는 통보</b>가 나간다.
     * RETURNING은 지운 행만 돌려주므로 이 어긋남이 구조적으로 불가능하다.</p>
     *
     * <p>소진된 신청({@code notified_at IS NOT NULL})은 건드리지 않는다. 그쪽은 이미 끝난
     * 의사표시의 이력이라 지울 이유가 없고, 지우면 "알림을 보낸 적 있다"를 잃는다.</p>
     *
     * @return 지운 행들의 {@code cohort_membership_id}. 삭제 통보의 수신자이며, 건수만
     *         필요한 호출자(MR-21)는 크기를 쓴다. 지운 행이 없으면 빈 목록
     */
    List<Long> deleteWaitingBySpaceId(Long spaceId);

    /**
     * 이 멤버십들이 신청한 대기 중 신청을 전부 지운다 (CE-02).
     *
     * <p>기수 종료 시 그 기수 신청만 지운다 — 다기수 담당자가 다른 기수 멤버십으로 신청한
     * 건은 멤버십이 다르므로 자연히 남는다 (명세 04 §2). 통보하지 않으므로
     * {@link #deleteWaitingBySpaceId}와 달리 수신자를 돌려줄 이유가 없다.</p>
     *
     * <p><b>이 삭제가 점유 종료(CE-03)보다 먼저여야 한다.</b> 뒤집히면 CE-03의 공실 발송이
     * 방금 종료된 기수의 신청을 아직 대기 중으로 보고 <b>그 학생들에게 알림을 보낸다</b>
     * (CE-05). 순서는 호출자가 지킨다.</p>
     *
     * @return 지운 건수
     */
    int deleteWaitingByMembershipIds(Collection<Long> cohortMembershipIds);
}
