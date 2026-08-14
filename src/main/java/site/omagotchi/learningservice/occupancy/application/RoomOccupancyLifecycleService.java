package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 시작된 점유의 연장·반납 (MR-06, MR-14, MR-32).
 *
 * <p>점유 시작({@code RoomOccupancyService})과 나눈 이유는 필요한 것이 다르기 때문이다.
 * 시작은 공간 유형·활성 여부와 재실을 확인해야 해서 {@code SpaceAccessService}·{@code AttendancePresenceQueryService}가
 * 필요하지만, 연장·반납은 이미 존재하는 점유 행의 상태만 바꾼다 — 요청자가 점유자인지만
 * 보면 되고 공간이나 재실은 다시 묻지 않는다.</p>
 *
 * <p>강제 종료(MR-21)도 같은 성격이라 여기 들어올 자리다. 아직 없는 이유는 요청자의
 * <b>활성 기수 집합</b>에서 점유자 기수의 매니저인지 판정해야 하는데(다기수 담당자 대응)
 * cohort 파트에 그 계약을 아직 요청하지 않았기 때문이다.</p>
 *
 * <p>두 메서드 모두 {@code room_occupancies} 행 락 하나만 잡는다. 점유 시작이
 * spaces → room_occupancies 순으로 잡으므로 부분집합이라 데드락이 없다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomOccupancyLifecycleService {

    private final RoomOccupancyRepository occupancyRepository;
    private final OccupancyParticipantRepository participantRepository;
    private final OccupancyEventPublisher eventPublisher;
    private final OccupancyExpiration occupancyExpiration;
    private final OccupancyExpiryReminder occupancyExpiryReminder;
    private final List<OccupancyReminderSender> reminderSenders;
    private final Clock clock;

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
     * 않는다. 구현이 둘 이상이면 어느 발송의 성공을 완료로 볼지 계약이 모호하므로 실패시킨다.</p>
     *
     * <p>후보는 한 번에 찾되 발송은 {@link OccupancyExpiryReminder}가 건별 트랜잭션에서
     * 처리한다. 한 건의 발송 실패는 다음 후보를 막지 않으며, 실패한 점유는 완료 기록이
     * 없어서 다음 주기에 다시 시도된다.</p>
     *
     * @return 실제 발송에 성공하고 완료 시각까지 기록한 점유 수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int sendExpiryReminders() {
        if (reminderSenders.isEmpty()) {
            log.debug("점유 만료 임박 알림 sender가 없어 이번 주기를 건너뜁니다.");
            return 0;
        }
        if (reminderSenders.size() > 1) {
            throw new IllegalStateException("점유 만료 임박 알림 sender는 하나만 등록할 수 있습니다.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime reminderEndsAt = now.plus(RoomOccupancy.EXPIRY_REMINDER_WINDOW);
        List<RoomOccupancyRepository.ExpiringOccupancy> candidates =
                occupancyRepository.findExpiringSoon(now, reminderEndsAt);
        OccupancyReminderSender sender = reminderSenders.getFirst();

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
}
