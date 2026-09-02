package site.omagotchi.learningservice.space.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("도서관 선택 공간 정책")
class StudySpaceSelectionServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @InjectMocks
    private StudySpaceSelectionService service;

    @Test
    @DisplayName("활성 STUDY 공간은 잠금 후 입장을 승인한다")
    void approvesActiveStudySpaceAfterLock() {
        when(spaceRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(space(SpaceType.STUDY, SpaceOperationalStatus.ACTIVE)));

        service.requireSelectableStudySpace(10L);

        verify(spaceRepository).findByIdForUpdate(10L);
    }

    @Test
    @DisplayName("STUDY가 아닌 공간은 도서관으로 선택할 수 없다")
    void rejectsNonStudySpace() {
        when(spaceRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(space(SpaceType.MEETING, SpaceOperationalStatus.ACTIVE)));

        assertThatThrownBy(() -> service.requireSelectableStudySpace(10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(SpaceErrorCode.STUDY_SPACE_NOT_SELECTABLE));
    }

    @Test
    @DisplayName("비활성 STUDY 공간은 도서관으로 선택할 수 없다")
    void rejectsInactiveStudySpace() {
        when(spaceRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(space(SpaceType.STUDY, SpaceOperationalStatus.INACTIVE)));

        assertThatThrownBy(() -> service.requireSelectableStudySpace(10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(SpaceErrorCode.STUDY_SPACE_NOT_SELECTABLE));
    }

    private Space space(SpaceType type, SpaceOperationalStatus status) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return Space.restore(
                10L,
                null,
                "도서관",
                type,
                100,
                status,
                status == SpaceOperationalStatus.INACTIVE ? "점검" : null,
                now.minusDays(1),
                now,
                null
        );
    }
}
