package site.omagotchi.learningservice.occupancy.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 시작된 점유의 연장·반납 (MR-06, MR-14, MR-32).
 *
 * <p>점유 시작({@code RoomOccupancyService})과 나눈 이유는 필요한 것이 다르기 때문이다.
 * 시작은 공간 유형·활성 여부와 재실을 확인해야 해서 {@code SpaceAccessService}·{@code AttendancePresenceQueryService}가
 * 필요하지만, 연장·반납은 이미 존재하는 점유 행의 상태만 바꾼다 — 요청자가 점유자인지만
 * 보면 되고 공간이나 재실은 다시 묻지 않는다.</p>
 *
 * <p>강제 종료(MR-21)도 같은 성격이라 여기 들어올 자리다. 필요한 계약은 갖춰졌다 —
 * 요청자의 <b>활성 기수 집합</b>에서 점유자 기수의 매니저인지 판정하는 것은
 * {@code CohortAccessService.isManager}가, 점유자 기수를 되찾는 것은
 * {@code CohortMembershipQueryService.findActiveMembership}이 한다. 남은 것은 대기 중
 * 공실 알림 삭제이며, {@code FORCE_RELEASED}는 {@code RoomVacatedEvent}를 발행하지 않는다.</p>
 *
 * <p>두 메서드 모두 {@code room_occupancies} 행 락 하나만 잡는다. 점유 시작이
 * spaces → room_occupancies 순으로 잡으므로 부분집합이라 데드락이 없다.</p>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class RoomOccupancyLifecycleService {

    private final RoomOccupancyRepository occupancyRepository;
    private final OccupancyParticipantRepository participantRepository;
    private final OccupancyEventPublisher eventPublisher;
    private final OccupancyExpiration occupancyExpiration;
    private final OccupancyExpiryReminder occupancyExpiryReminder;
    /**
     * 발송 수단. 비어 있는 것은 정상 상태이며(아직 발송 수단이 없다) 그때는 후보를
     * 소진시키지 않는다 ({@code VacancyAlertDispatcher}와 같은 규약).
     */
    private final Optional<OccupancyReminderSender> reminderSender;
    private final CohortAccessService cohortAccessService;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final VacancyAlertRepository alertRepository;
    private final Clock clock;

    /**
     * <b>{@code List}로 받아야 한다.</b> 0개를 허용해야 하므로 단건 주입은 쓸 수 없고,
     * {@code Optional}로 받으면 후보가 둘이어도 {@code @Primary} 하나가 모호성을 없애 버려
     * <b>나머지가 조용히 무시된다.</b> {@code List}만 후보 전부를 보여 준다.
     */
    public RoomOccupancyLifecycleService(
            RoomOccupancyRepository occupancyRepository,
            OccupancyParticipantRepository participantRepository,
            OccupancyEventPublisher eventPublisher,
            OccupancyExpiration occupancyExpiration,
            OccupancyExpiryReminder occupancyExpiryReminder,
            List<OccupancyReminderSender> reminderSenders,
            CohortAccessService cohortAccessService,
            CohortMembershipQueryService cohortMembershipQueryService,
            VacancyAlertRepository alertRepository,
            Clock clock
    ) {
        this.occupancyRepository = occupancyRepository;
        this.participantRepository = participantRepository;
        this.eventPublisher = eventPublisher;
        this.occupancyExpiration = occupancyExpiration;
        this.occupancyExpiryReminder = occupancyExpiryReminder;
        // 어느 발송의 성공을 완료로 볼지 정할 수 없는 설정이므로, 스케줄러가 도는 순간이
        // 아니라 기동 시점에 멈춘다.
        if (reminderSenders.size() > 1) {
            throw new IllegalStateException(
                    "점유 만료 임박 알림 sender는 하나만 등록할 수 있습니다: " + reminderSenders);
        }
        this.reminderSender = reminderSenders.stream().findFirst();
        this.cohortAccessService = cohortAccessService;
        this.cohortMembershipQueryService = cohortMembershipQueryService;
        this.alertRepository = alertRepository;
        this.clock = clock;
    }

    /**
     * 점유를 30분 연장한다 (MR-06, MR-12).
     *
     * @return 갱신된 만료 시각과 남은 시간. 클라이언트가 타이머를 다시 맞추는 데 쓴다
     * @throws BusinessException 요청자가 점유자 아님(403),
     *                           종료·만료된 점유·너무 이른 연장·횟수 초과(409)
     */
    @Transactional
    public RoomOccupancyResult extend(Long spaceId, UUID userId) {

        RoomOccupancy occupancy = lockOwnedActiveOccupancy(spaceId, userId);
        OffsetDateTime now = OffsetDateTime.now(clock);

        // 만료 판정이 먼저다. 스케줄러(#9)가 아직 EXPIRED로 바꾸지 않아 status는 ACTIVE인
        // 창이 있고, 그때 만료된 점유를 연장시키면 사실상 죽은 점유가 되살아난다.
        // 순서를 뒤로 미루면 이미 끝난 점유가 "아직 이릅니다"라는 엉뚱한 안내를 받는다.
        if (occupancy.isExpiredAt(now)) {
            throw new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED);
        }
        if (!occupancy.isWithinExtensionWindow(now)) {
            throw new BusinessException(OccupancyErrorCode.EXTENSION_TOO_EARLY);
        }
        if (!occupancy.hasRemainingExtension()) {
            throw new BusinessException(OccupancyErrorCode.EXTENSION_LIMIT_EXCEEDED);
        }

        // save를 부르지 않는다. 락으로 읽은 엔티티는 영속성 컨텍스트가 관리하므로
        // 필드 변경이 커밋 시점에 UPDATE로 나간다.
        occupancy.extend();

        return RoomOccupancyResult.of(occupancy, now);
    }

    /**
     * 점유를 반납한다 (MR-14, MR-32).
     *
     * <p>참여자 마감과 상태 변경이 한 트랜잭션이어야 한다. 끊기면 종료된 점유에
     * {@code left_at IS NULL} 행이 남고, 그 사람들이 영구히 다른 회의에 참여할 수 없게 된다.</p>
     *
     * @throws BusinessException 요청자가 점유자 아님(403), 이미 종료된 점유(409)
     */
    @Transactional
    public void release(Long spaceId, UUID userId) {

        RoomOccupancy occupancy = lockOwnedActiveOccupancy(spaceId, userId);
        OffsetDateTime now = OffsetDateTime.now(clock);

        // 반환값을 보지 않는 것이 의도다. lockOwnedActiveOccupancy가 락을 잡은 뒤
        // isActive()를 이미 확인했으므로 여기서 false가 나올 수 없다 — 같은 인스턴스이고
        // 그 사이 상태를 바꾸는 코드가 없다. 도메인의 멱등 가드는 스케줄러처럼 게이트를
        // 거치지 않는 다른 호출자를 위한 것이며, 그쪽 검증은 RoomOccupancyTest가 담당한다.
        occupancy.release(now);

        // 점유자 본인의 참여 행도 여기서 함께 닫힌다 — 시작이 점유자를 참여자로
        // 등록했으므로(MR-27) 별도 처리가 필요 없다.
        int closed = participantRepository.closeAllActiveByOccupancyId(occupancy.getId(), now);
        log.debug("반납으로 참여자 {}명을 마감했습니다. occupancyId={}", closed, occupancy.getId());

        // 발행은 상태 변경 뒤다. 리스너가 AFTER_COMMIT으로 받으므로 실제 발송은 커밋 후이며,
        // 발송 실패가 이 트랜잭션을 롤백시키지 않는다 (MR-18, ADR-0006).
        eventPublisher.publishRoomVacated(
                new RoomVacatedEvent(spaceId, occupancy.getId(), now));
    }

    /**
     * 기수 매니저가 점유를 강제 종료한다 (MR-21).
     *
     * <p><b>반납({@link #release})과 후속 처리가 정반대다.</b> 저쪽은 공실 알림을 발송하지만
     * 여기는 <b>발송하지 않고 대기 신청을 지운다</b> — 공간 회수가 목적이라 곧 쓸 수 없는
     * 방을 대기자에게 알리면 안 되고, 신청을 남겨 두면 다음 공실에 <b>회수된 방을 기다리던
     * 사람들</b>에게 알림이 나간다.</p>
     *
     * <p>권한을 <b>점유자의 기수</b>로 판정하는 것이 요점이다 (명세 02 §2). 요청자의 기수가
     * 아니라 점유자의 기수에서 매니저여야 하며, 다기수 담당자 때문에 단일 기수 비교가 아니라
     * 그 기수를 지정한 판정을 쓴다.</p>
     *
     * <p>참여자 마감·신청 삭제를 <b>같은 Transaction</b>에 두는 것도 명세가 정한 것이다.
     * 상태만 바꾸고 참여자를 열어 두면 {@code uq_occupancy_participants_one_active}가 계정
     * 기준이라 그 사람들이 영구히 다른 회의에 들어갈 수 없다.</p>
     *
     * @throws BusinessException 점유자 기수의 매니저 아님(403),
     *                           활성 점유 없음·이미 종료·<b>점유자 멤버십 종료</b>(409)
     */
    @Transactional
    public void forceRelease(Long spaceId, UUID actorUserId) {

        // 락 밖에서 값으로 읽는다. 여기서 엔티티를 읽으면 1차 캐시에 올라가
        // 아래 lockById()의 상태 재확인이 락 이전 스냅샷을 보게 된다.
        RoomOccupancyRepository.ActiveOccupancy summary =
                occupancyRepository.findActiveSummaryBySpaceId(spaceId)
                        .orElseThrow(() -> new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED));

        requireOccupierCohortManager(summary.occupierMembershipId(), actorUserId);

        RoomOccupancy occupancy = occupancyRepository.lockById(summary.id())
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED));
        if (!occupancy.isActive()) {
            throw new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        occupancy.forceRelease(now);

        int closed = participantRepository.closeAllActiveByOccupancyId(occupancy.getId(), now);
        // 지운 행의 멤버십이 돌아오지만 쓰지 않는다 — 강제 종료는 통보하지 않는 삭제다 (MR-21).
        int discarded = alertRepository.deleteWaitingBySpaceId(spaceId).size();

        // RoomVacatedEvent를 발행하지 않는 것이 이 경로의 정의다 (MR-21).
        log.info("점유를 강제 종료했습니다. spaceId={}, occupancyId={}, 참여자 마감={}건, 대기 신청 삭제={}건",
                spaceId, occupancy.getId(), closed, discarded);
    }

    /**
     * 만료된 점유를 일괄 종료한다 (스케줄러 #9).
     *
     * <p>점유 시작이 대행하는 {@code expireStale*}와 <b>목적이 다르다.</b> 저쪽은 요청 경로에서
     * 불필요한 409를 줄이는 최선 노력이라 그 공간·그 계정만 훑지만, 여기는 아무도 찾지 않는
     * 방까지 정리한다 — 만료된 채 방치되면 공간 목록에 계속 "사용 중"으로 뜨고 참여자도
     * 열린 채 남는다.</p>
     *
     * <p><b>이 경로가 공실 알림의 정본 발행 지점이다.</b> 만료는 사용자 행위 없이 일어나므로
     * 여기서 발행하지 않으면 "2시간 다 써서 비었다"는 가장 흔한 공실이 아무에게도
     * 전달되지 않는다 (MR-03).</p>
     *
     * <p><b>이 Method에 트랜잭션이 없는 것이 의도다.</b> 명세서 03이 "한 건의 실패가 나머지를
     * 막지 않도록 건별로 처리한다"고 정했고, 트랜잭션이 있으면 {@link OccupancyExpiration}의
     * {@code REQUIRES_NEW}가 매 건 바깥 트랜잭션을 중단시켰다 재개하는 낭비가 되고, 조회 한 번을
     * 위해 커넥션을 후보 전체 처리가 끝날 때까지 붙들게 된다. 조회는 단일 SELECT라 트랜잭션이
     * 필요 없다.</p>
     *
     * <p>{@code Propagation.NOT_SUPPORTED}를 명시하는 것이 핵심이다. 클래스 레벨
     * {@code @Transactional(readOnly = true)}는 Method에 별도 선언이 없으면 그대로 상속되므로,
     * 여기 명시하지 않으면 이 문서가 말하는 "트랜잭션 없음"이 실제로는 성립하지 않는다.</p>
     *
     * <p>건별 실패를 여기서 잡아 다음 건으로 넘어간다. 실패한 건은 상태가 그대로 ACTIVE라
     * 다음 주기가 다시 집어 간다 — 전이가 조건부({@code status='ACTIVE' AND expires_at <= now})라
     * 재실행이 안전하다.</p>
     *
     * <p>조회 결과가 곧 종료 건수는 아니다. 조회와 전이 사이에 연장·반납이 일어나거나 다른
     * 인스턴스가 먼저 처리하면 그 건은 건너뛴다 — 판정은 전부
     * {@link RoomOccupancyRepository#expire}의 조건이 한다.</p>
     *
     * @return 이번 실행으로 실제 종료된 점유 수. 조회된 후보 수보다 적을 수 있다
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int expireAll() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<RoomOccupancyRepository.ExpiredOccupancy> candidates = occupancyRepository.findStale(now);

        int expired = 0;
        for (RoomOccupancyRepository.ExpiredOccupancy candidate : candidates) {
            try {
                if (occupancyExpiration.expire(candidate, now)) {
                    expired++;
                }
            } catch (Exception exception) {
                // 건별 격리. 여기서 다시 던지면 남은 점유가 이번 주기에 처리되지 않고,
                // 그것이 명세서 03이 건별 트랜잭션을 요구한 이유다.
                log.error("점유 만료 처리에 실패했습니다. occupancyId={}", candidate.occupancyId(), exception);
            }
        }

        if (expired > 0) {
            log.info("만료된 점유 {}건을 정리했습니다.", expired);
        }
        return expired;
    }

    /**
     * 만료까지 10분 이하로 남은 ACTIVE 점유의 점유자에게 알림을 보낸다 (MR-12).
     *
     * <p>실제 발송 계약 구현이 없으면 후보를 조회하거나 {@code reminder_sent_at}을 소진하지
     * 않는다. 구현이 둘 이상이면 어느 발송의 성공을 완료로 볼지 계약이 모호한데, 그 설정은
     * 여기까지 오지 못한다 — {@code Optional} 주입이라 Container가 기동 시점에 거부한다.</p>
     *
     * <p>후보는 한 번에 찾되 발송은 {@link OccupancyExpiryReminder}가 건별 트랜잭션에서
     * 처리한다. 한 건의 발송 실패는 다음 후보를 막지 않으며, 실패한 점유는 완료 기록이
     * 없어서 다음 주기에 다시 시도된다.</p>
     *
     * @return 실제 발송에 성공하고 완료 시각까지 기록한 점유 수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int sendExpiryReminders() {
        if (reminderSender.isEmpty()) {
            log.debug("점유 만료 임박 알림 sender가 없어 이번 주기를 건너뜁니다.");
            return 0;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime reminderEndsAt = now.plus(RoomOccupancy.EXPIRY_REMINDER_WINDOW);
        List<RoomOccupancyRepository.ExpiringOccupancy> candidates =
                occupancyRepository.findExpiringSoon(now, reminderEndsAt);
        OccupancyReminderSender sender = reminderSender.get();

        int sent = 0;
        for (RoomOccupancyRepository.ExpiringOccupancy candidate : candidates) {
            try {
                if (occupancyExpiryReminder.send(candidate, sender)) {
                    sent++;
                }
            } catch (Exception exception) {
                log.error("점유 만료 임박 알림에 실패했습니다. occupancyId={}",
                        candidate.occupancyId(), exception);
            }
        }

        if (sent > 0) {
            log.info("점유 만료 임박 알림 {}건을 발송했습니다.", sent);
        }
        return sent;
    }

    // ────────────────────────────── 내부 헬퍼 ──────────────────────────────

    /**
     * 요청자가 점유자인 활성 점유를 락으로 잡아 돌려준다.
     *
     * <p>권한 판정을 락 밖에서 먼저 하는 것이 요점이다. 남의 점유에 락을 걸어두고
     * 403을 던지면 그동안 정당한 요청까지 대기한다.</p>
     *
     * <p>값 조회와 락 조회를 나눈 이유는 1차 캐시다. 락 전에 엔티티를 읽으면 그 인스턴스가
     * 영속성 컨텍스트에 올라가, 뒤이은 {@code FOR UPDATE}가 락 이전 스냅샷을 돌려준다.</p>
     */
    private RoomOccupancy lockOwnedActiveOccupancy(Long spaceId, UUID userId) {
        RoomOccupancyRepository.ActiveOccupancy summary =
                occupancyRepository.findActiveSummaryBySpaceId(spaceId)
                        .orElseThrow(() -> new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED));

        if (!summary.occupierUserId().equals(userId)) {
            throw new BusinessException(OccupancyErrorCode.NOT_OCCUPIER);
        }

        RoomOccupancy locked = occupancyRepository.lockById(summary.id())
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED));

        // 활성 조건을 락 쿼리에 넣지 않고 잡은 뒤 확인한다. 그래야 "스케줄러가 EXPIRED로
        // 바꾼 직후 도착한 요청"을 행 없음(404)이 아니라 409로 정확히 잡는다.
        if (!locked.isActive()) {
            throw new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED);
        }
        return locked;
    }

    /**
     * 요청자가 점유자 기수의 매니저인지 확인한다 (MR-21).
     *
     * <p>점유 행에 {@code cohort_id}가 없으므로(ERD v3) {@code occupier_membership_id}로
     * 기수를 되찾는다.</p>
     *
     * <p><b>두 실패를 다른 코드로 구분한다.</b> 멤버십이 이미 끝났으면 기수를 특정할 수 없어
     * 권한 판정 자체가 성립하지 않는데, 이것을 권한 없음(403)으로 돌려주면 매니저가 <b>자기
     * 권한을 의심하게 된다.</b> 원인은 요청자가 아니라 데이터 쪽이므로 참여자 추가 경로와
     * 같은 {@code OCCUPIER_MEMBERSHIP_INACTIVE}(409)를 쓴다.</p>
     *
     * <p><b>정상 경로에서는 이 상태에 도달하지 않는다.</b> 소속이 끝나면
     * {@code OccupancyMembershipEndedListener}가 점유를 반납 처리하므로(MR-26), 여기까지
     * 왔다는 것은 그 정리가 일어나지 않았다는 뜻이다 — 이벤트 유실, 또는 이벤트를 거치지
     * 않고 멤버십이 바뀐 경우다.</p>
     *
     * <p><b>그 복구 경로는 아직 없다.</b> 팀이 같은 이벤트에 대해
     * {@code EndedMembershipSweep}(ADR space-team/0013)을 둔 것과 같은 정합성 스윕이
     * 필요하며, 그때까지는 만료 스케줄러(#9)가 {@code expires_at} 경과 후 정리한다 —
     * 최대 3시간(2h + 연장 1h) 동안 방이 잠기고, 참여자들은
     * {@code uq_occupancy_participants_one_active}에 묶여 다른 회의에 들어가지 못한다.</p>
     */
    private void requireOccupierCohortManager(Long occupierMembershipId, UUID actorUserId) {
        Long occupierCohortId = cohortMembershipQueryService
                .findActiveMembership(occupierMembershipId)
                .map(CohortMembershipView::cohortId)
                .orElseThrow(() -> new BusinessException(
                        OccupancyErrorCode.OCCUPIER_MEMBERSHIP_INACTIVE));

        if (!cohortAccessService.isManager(occupierCohortId, actorUserId)) {
            throw new BusinessException(OccupancyErrorCode.NOT_COHORT_MANAGER);
        }
    }
}
