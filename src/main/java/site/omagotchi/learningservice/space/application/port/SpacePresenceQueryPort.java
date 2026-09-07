package site.omagotchi.learningservice.space.application.port;

import site.omagotchi.learningservice.space.application.result.SpacePresenceSummary;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 공간 비활성화와 실습실 정원 판단에 쓰는 현재 체류 조회 경계. */
public interface SpacePresenceQueryPort {

    Map<Long, SpacePresenceSummary> summarize(
            Collection<Long> spaceIds,
            LocalDate attendanceDate
    );

    Map<Long, Long> findCurrentCounts(
            Collection<Long> spaceIds,
            LocalDate attendanceDate
    );

    boolean isReserved(Long spaceId, Long attendanceId, LocalDate attendanceDate);

    /** 현재 이 공간에 머무는 사람 중 선택 기수에 속한 계정 식별자를 일괄 조회한다. */
    List<UUID> findCurrentUserIds(Long spaceId, Long cohortId, LocalDate attendanceDate);
}
