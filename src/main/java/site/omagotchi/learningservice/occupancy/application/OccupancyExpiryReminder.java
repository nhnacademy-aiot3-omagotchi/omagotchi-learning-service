package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;
import site.omagotchi.learningservice.space.application.SpaceNameQueryService;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 만료 임박 점유 한 건을 잠그고 실제 발송 성공 뒤에만 완료 시각을 기록한다.
 *
 * <p>행 잠금은 여러 애플리케이션 인스턴스의 동시 발송과 연장·반납 경합을 직렬화한다.
 * 실제 sender 호출 동안 점유 한 행의 잠금이 유지되므로 sender 구현은 동기 성공 계약과
 * 제한된 호출 시간을 가져야 한다. 전체 후보를 한 트랜잭션으로 묶지는 않는다.</p>
 *
 * <p>외부 발송과 DB 기록은 하나의 원자적 트랜잭션이 아니다. 따라서 발송은 성공했지만
 * 이 트랜잭션의 커밋이 실패하면 다음 주기에 같은 알림이 다시 발송될 수 있다. 다중
 * 인스턴스의 정상 커밋 경로는 행 잠금과 재검증으로 직렬화하지만, 이 실패 구간까지
 * exactly-once로 만들려면 sender가 {@code occupancyId + expiresAt}을 멱등 키로 받거나
 * 향후 outbox를 도입해야 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OccupancyExpiryReminder {

    private final RoomOccupancyRepository occupancyRepository;
    private final SpaceNameQueryService spaceNameQueryService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean send(
            RoomOccupancyRepository.ExpiringOccupancy candidate,
            OccupancyReminderSender sender
    ) {
        RoomOccupancy occupancy = occupancyRepository.lockById(candidate.occupancyId())
                .orElse(null);
        OffsetDateTime now = OffsetDateTime.now(clock);

        // 후보 조회 뒤 연장되었으면 예전 만료 시각의 알림을 보내면 안 된다.
        if (occupancy == null
                || !occupancy.getExpiresAt().equals(candidate.expiresAt())
                || !occupancy.isExpiryReminderDueAt(now)) {
            return false;
        }

        // 조회 실패(예: 공간이 그 사이 삭제됨)가 알림 자체를 막으면 안 되므로 spaceId로
        // 대체할 뿐 예외를 던지지 않는다 — VacancyAlertDispatcher와 같은 판단이다.
        String spaceName = spaceNameQueryService.findName(occupancy.getSpaceId())
                .orElse("공간 " + occupancy.getSpaceId());

        boolean sent = sender.sendExpiryReminder(new OccupancyReminderSender.ExpiryReminder(
                occupancy.getId(),
                occupancy.getSpaceId(),
                spaceName,
                occupancy.getOccupierUserId(),
                occupancy.getExpiresAt()
        ));
        if (!sent) {
            // 수신자가 미연동이거나 알림을 꺼서 건너뛴 경우다. 기록하지 않아야 다음
            // 스케줄러 주기에 다시 시도된다 — 여기서 소진하면 나중에 연동해도 못 받는다.
            return false;
        }

        // sender가 실제 성공을 뜻하는 true를 반환한 경우에만 기록한다.
        occupancy.markExpiryReminderSent(now);
        return true;
    }
}
