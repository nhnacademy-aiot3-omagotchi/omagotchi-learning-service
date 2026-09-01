package site.omagotchi.learningservice.space.application.port;

import site.omagotchi.learningservice.space.application.result.SpacePresenceSummary;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** 공간 비활성화와 실습실 정원 판단에 쓰는 현재 체류 조회 경계. */
public interface SpacePresenceQueryPort {

    Map<Long, SpacePresenceSummary> summarize(
            Collection<Long> spaceIds,
            LocalDate attendanceDate
    );

    boolean isReserved(Long spaceId, Long attendanceId, LocalDate attendanceDate);
}
