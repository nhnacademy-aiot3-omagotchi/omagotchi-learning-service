package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView;
import site.omagotchi.learningservice.attendance.application.port.AttendancePresenceQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 다른 Feature가 재실 여부를 묻는 공개 계약.
 *
 * <p>{@link AttendanceService}와 나눠 둔 이유는 역할이 다르기 때문이다. 저쪽은 출퇴근을
 * 기록하는 명령이고, 여기는 "지금 안에 있는가"를 답하는 조회다. 첫 소비처는 점유의
 * 재실 검증(MR-22, MR-19)이다.</p>
 *
 * <p>예외가 아니라 {@link Optional}을 돌려준다. "재실 아님"이 점유 시작에서는 403
 * {@code NOT_PRESENT}, 참여자 추가에서는 403 {@code TARGET_NOT_PRESENT}로 갈리므로
 * 판단은 호출부가 한다.</p>
 *
 * <p>재실 판정 자체(어떤 {@code state}를 재실로 볼지)는 이쪽 책임이다. 받는 쪽이
 * 상태값을 보고 다시 판정하면 파트마다 기준이 갈린다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendancePresenceQueryService {

    private final AttendancePresenceQuery attendancePresenceQuery;

    /**
     * 지금 재실 중인지 조회한다.
     *
     * <p><b>열린 구간이 여럿이면 가장 최근에 시작한 것을 돌려준다.</b> 다기수 담당
     * 매니저·멘토는 기수마다 {@code attendance_records}가 따로 생겨 두 기수에 동시에
     * 출근 처리될 수 있는데, 그때 어느 기수로 점유할지를 서버가 정해야 한다 — 명세서 02가
     * "다기수 담당자도 별도 선택 없이 결정된다"고 전제하므로 요청에 기수를 받지 않는다.</p>
     *
     * <p>최신 기준을 고른 것은 "방금 출근한 쪽이 지금 활동 중인 기수"라는 판단이며,
     * 결정적이라 재시도에도 같은 결과가 나온다. <b>다만 확정된 정책은 아니다</b> —
     * 실무에서 한 사람이 같은 날 두 기수에 동시 출근하는 일이 있는지 확인되면 규칙을
     * 다시 정해야 한다.</p>
     *
     * @return 열린 재실 구간이 없으면 {@code Optional.empty()}
     */
    public Optional<OpenPresenceView> findOpenPresence(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }

        List<OpenPresenceView> openPresences = attendancePresenceQuery.findOpenPresences(userId);
        if (openPresences.size() > 1) {
            log.debug("열린 재실 구간이 {}개입니다. 최신 구간을 사용합니다. userId={}",
                    openPresences.size(), userId);
        }
        return openPresences.stream().findFirst();
    }

    /** 계정별 최신 열린 재실 구간을 한 번에 조회한다. */
    public Map<UUID, OpenPresenceView> findOpenPresences(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, OpenPresenceView> latestByUserId = new LinkedHashMap<>();
        for (OpenUserPresenceView presence : attendancePresenceQuery.findOpenPresences(userIds)) {
            latestByUserId.putIfAbsent(presence.userId(), presence.toPresenceView());
        }
        return Map.copyOf(latestByUserId);
    }
}
