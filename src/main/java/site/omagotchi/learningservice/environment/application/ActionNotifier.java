package site.omagotchi.learningservice.environment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.environment.application.port.ActionNotificationSender;
import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.rule.application.SensorDeviceService;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 자동 조치 결과를 <b>그 공간을 담당하는 기수의 매니저</b>에게 알린다.
 *
 * <p>매니저는 자기 기수의 일만 본다. 센서 이벤트는 기기까지만 알고 기수는 모르므로
 * {@code deviceEui → 공간 → 기수}로 되짚는다.</p>
 *
 * <p><b>기수를 특정하지 못하면 보내지 않는다.</b> 그 상태는 센서나 공간의 배정이 빠진
 * 설정 누락인데, 전원에게 보내는 것으로 덮으면 남의 기수 실습실 이야기가 섞이고
 * 배정이 빠진 사실도 드러나지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionNotifier {
    private final CohortMembershipQueryService membershipQueryService;
    private final SensorDeviceService sensorDeviceService;
    private final SpaceCohortQueryService spaceCohortQueryService;
    private final EnvironmentProperties properties;
    private final Clock clock;
    private final ActionNotificationSender sender;

    public Instant notifyConfirmed(SensorDetection detection, IotAction action, IotActionResult result){
        Long cohortId = cohortIdOf(detection);
        if(Objects.isNull(cohortId)){
            log.warn("담당 기수를 찾지 못해 조치 알림을 보내지 않습니다. location={}, deviceEui={}",
                    detection.location(), detection.deviceEui());

            return null;
        }

        List<UUID> managerIds = membershipQueryService.findActiveManagerUserIds(cohortId);
        if(managerIds.isEmpty()){
            log.info("기수에 활성 관리자가 없습니다. cohortId={}, location={}, action={}",
                    cohortId, detection.location(), action);

            return null;
        }

        long deadline = System.nanoTime() + properties.notifyDeadline().toNanos();


        int sent = 0;
        for(UUID recipientUserId : managerIds){
            // 남은 예산을 그대로 넘긴다. 검사만 하고 넘기지 않으면 마지막 한 건이 예산을
            // 넘겨 시작해, MQ 리스너가 예산 + read 타임아웃만큼 묶인다.
            long remainingNanos = deadline - System.nanoTime();
            if(remainingNanos <= 0){
                log.warn("조치 알림 발송 데드라인을 넘겨 중단합니다. location={}", detection.location());
                break;
            }

            try{
                boolean success = sender.send(
                        ActionNotificationSender.ActionNotice.of(recipientUserId, detection, action, result),
                        Duration.ofNanos(remainingNanos)
                );

                if(success){
                    sent++;
                }

            }catch (Exception e){
                log.warn("조치 알림 발송에 실패했습니다. location={}, userId={}", detection.location(), recipientUserId, e);
            }
        }

        return sent > 0 ? clock.instant() : null;
    }

    /**
     * 감지가 일어난 공간의 담당 기수. 되짚지 못하면 {@code null}이다.
     *
     * <p>{@code deviceEui}는 이벤트 종류에 따라 없을 수 있고({@code SensorDetection}은
     * {@code type}과 {@code receivedAt}만 보장한다), 기기에 공간이, 공간에 기수가 배정되지
     * 않았을 수도 있다. 셋 다 "담당 기수를 말할 수 없다"는 같은 결론이라 구분하지 않는다.</p>
     */
    private Long cohortIdOf(SensorDetection detection){
        String deviceEui = detection.deviceEui();

        if(Objects.isNull(deviceEui) || deviceEui.isBlank()){
            return null;
        }

        Optional<Long> spaceId = sensorDeviceService.findSpaceId(deviceEui);
        if(spaceId.isEmpty()){
            return null;
        }

        return spaceCohortQueryService.findCohortId(spaceId.get()).orElse(null);
    }
}
