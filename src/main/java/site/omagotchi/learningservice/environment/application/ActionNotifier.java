package site.omagotchi.learningservice.environment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.environment.application.port.ActionNotificationSender;
import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.environment.domain.SensorDetection;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionNotifier {
    private final CohortMembershipQueryService membershipQueryService;
    private final EnvironmentProperties properties;
    private final Clock clock;
    private final ActionNotificationSender sender;

    public Instant notifyConfirmed(SensorDetection detection, IotAction action, IotActionResult result){
        List<UUID> managerIds = membershipQueryService.findActiveManagerUserIds();
        if(managerIds.isEmpty()){
            log.info("활성된 기수 관리자가 없습니다. location={}, action={}", detection.location(), action);

            return null;
        }

        long deadline = System.nanoTime() + properties.notifyDeadline().toNanos();


        int sent = 0;
        for(UUID recipientUserId : managerIds){
            if(System.nanoTime() > deadline){
                log.warn("조치 알림 발송 데드라인을 넘겨 중단합니다. location={}", detection.location());
                break;
            }

            try{
                boolean success = sender.send(
                        ActionNotificationSender.ActionNotice.of(recipientUserId, detection, action, result)
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
}
