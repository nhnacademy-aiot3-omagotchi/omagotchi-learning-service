package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.port.SpaceReader;
import site.omagotchi.learningservice.space.application.SpaceAccessService;
import site.omagotchi.learningservice.space.application.result.SpaceAccessView;

import java.util.Optional;

/**
 * {@link SpaceReader}를 {@code space} 파트의 공개 계약으로 구현한다.
 *
 *
 * <p>얇은 위임인데도 Class를 두는 이유는 {@link SpaceAccessView}가 {@code space}의 Type이기
 * 때문이다. Application이 이것을 직접 받으면 점유의 Use Case가 남의 계약 변화에 흔들린다 —
 * 경계에서 한 번 {@link SpaceReader.MeetingRoom}으로 옮긴다.</p>
 *
 * <p>값을 돌려주는 계약은 그대로다. 엔티티를 받지 않으므로 락 이후 상태 재확인이 1차 캐시에
 * 가려지는 함정이 여전히 없다 — 자세한 근거는 {@link SpaceReader} javadoc 참고.</p>
 */
@Component
@RequiredArgsConstructor
public class SpaceAccessReader implements SpaceReader {

    private final SpaceAccessService spaceAccessService;

    @Override
    public Optional<MeetingRoom> find(Long spaceId) {
        return spaceAccessService.find(spaceId).map(SpaceAccessReader::toMeetingRoom);
    }

    @Override
    public Optional<MeetingRoom> lock(Long spaceId) {
        return spaceAccessService.lock(spaceId).map(SpaceAccessReader::toMeetingRoom);
    }

    private static MeetingRoom toMeetingRoom(SpaceAccessView view) {
        return new MeetingRoom(
                view.spaceId(),
                view.meetingRoom(),
                view.active(),
                view.capacity()
        );
    }
}
