package site.omagotchi.learningservice.sensor.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.command.CreateSensorDeviceCommand;
import site.omagotchi.learningservice.sensor.application.command.UpdateSensorDeviceCommand;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorDeviceServiceTest {

    private static final Long COHORT_ID = 1L;
    private static final Long SPACE_ID = 10L;
    /** 기수 배정이 풀렸거나 삭제된 공간. 그 공간의 센서가 주인 없는 상태다. */
    private static final Long ORPHAN_SPACE_ID = 90L;
    private static final Long OTHER_SPACE_ID = 91L;
    private static final UUID REQUESTER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final String DEVICE_EUI = "0011223344556677";
    private static final Instant INSTALLED_AT = Instant.parse("2026-08-30T00:00:00Z");

    @Mock
    private SensorDeviceRepository sensorDeviceRepository;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private SpaceCohortQueryService spaceCohortQueryService;

    @InjectMocks
    private SensorDeviceService sensorDeviceService;

    @Test
    void createsSensorOnlyAfterManagerAndSpaceOwnershipChecks() {
        when(spaceCohortQueryService.findCohortId(SPACE_ID))
                .thenReturn(Optional.of(COHORT_ID));
        when(sensorDeviceRepository.save(any(SensorDevice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String createdEui = sensorDeviceService.create(
                COHORT_ID,
                REQUESTER_ID,
                createCommand()
        );

        assertThat(createdEui).isEqualTo(DEVICE_EUI);
        verify(cohortAccessService).requireManager(COHORT_ID, REQUESTER_ID);
        ArgumentCaptor<SensorDevice> saved = ArgumentCaptor.forClass(SensorDevice.class);
        verify(sensorDeviceRepository).save(saved.capture());
        assertThat(saved.getValue().getSpaceId()).isEqualTo(SPACE_ID);
    }

    @Test
    void rejectsCreateWhenRequesterIsNotManagerBeforeReadingResources() {
        BusinessException denied = new BusinessException(
                CohortErrorCode.COHORT_MANAGER_REQUIRED
        );
        doThrow(denied)
                .when(cohortAccessService)
                .requireManager(COHORT_ID, REQUESTER_ID);

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                sensorDeviceService.create(
                        COHORT_ID,
                        REQUESTER_ID,
                        createCommand()
                )
        );

        assertThat(thrown.getErrorCode())
                .isEqualTo(CohortErrorCode.COHORT_MANAGER_REQUIRED);
        verifyNoInteractions(sensorDeviceRepository, spaceCohortQueryService);
    }

    @Test
    void rejectsUpdatingSensorOwnedByAnotherCohort() {
        SensorDevice device = device(20L);
        when(sensorDeviceRepository.findByDeviceEui(DEVICE_EUI))
                .thenReturn(Optional.of(device));
        when(spaceCohortQueryService.findCohortId(20L))
                .thenReturn(Optional.of(2L));

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                sensorDeviceService.update(
                        COHORT_ID,
                        REQUESTER_ID,
                        DEVICE_EUI,
                        updateCommand()
                )
        );

        assertThat(thrown.getErrorCode()).isEqualTo(SensorErrorCode.DEVICE_NOT_FOUND);
        verify(sensorDeviceRepository, never()).save(any());
    }

    @Test
    void scopesListToCohortSpacesAfterMembershipCheck() {
        SensorDevice device = device(SPACE_ID);
        when(spaceCohortQueryService.findSpaceIdsByCohortId(COHORT_ID))
                .thenReturn(List.of(SPACE_ID));
        when(sensorDeviceRepository.findBySpaceIds(List.of(SPACE_ID)))
                .thenReturn(List.of(device));

        var results = sensorDeviceService.findAll(COHORT_ID, REQUESTER_ID);

        // 읽기는 소속이면 된다. 매니저로 올리면 여기서 걸린다
        verify(cohortAccessService).requireActiveMembershipId(COHORT_ID, REQUESTER_ID);
        assertThat(results)
                .extracting(result -> result.deviceEui())
                .containsExactly(DEVICE_EUI);
    }

    @Test
    void rejectsListingSensorsWhenRequesterIsNotMember() {
        doThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND))
                .when(cohortAccessService)
                .requireActiveMembershipId(COHORT_ID, REQUESTER_ID);

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                sensorDeviceService.findAll(COHORT_ID, REQUESTER_ID)
        );

        // 소속이 아니면 기수 존재 자체를 숨긴다 — 403이 아니라 404다
        assertThat(thrown.getErrorCode())
                .isEqualTo(CohortErrorCode.COHORT_NOT_FOUND);
        verifyNoInteractions(sensorDeviceRepository, spaceCohortQueryService);
    }

    @Test
    void claimsOrphanSensorIntoCohortSpace() {
        // 이전 기수가 쓰다 회수된 센서 — 붙어 있던 공간은 기수가 풀렸다
        SensorDevice orphan = device(ORPHAN_SPACE_ID);
        orphan.changeActive(false);
        when(spaceCohortQueryService.findCohortId(SPACE_ID)).thenReturn(Optional.of(COHORT_ID));
        when(sensorDeviceRepository.findByDeviceEui(DEVICE_EUI)).thenReturn(Optional.of(orphan));
        when(spaceCohortQueryService.findCohortId(ORPHAN_SPACE_ID)).thenReturn(Optional.empty());
        when(sensorDeviceRepository.save(any(SensorDevice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = sensorDeviceService.claim(COHORT_ID, REQUESTER_ID, DEVICE_EUI, SPACE_ID);

        verify(cohortAccessService).requireManager(COHORT_ID, REQUESTER_ID);
        assertThat(result.spaceId()).isEqualTo(SPACE_ID);
        // 회수 상태로 넘어온 센서를 다시 켜지 않으면 집계에도 룰에도 잡히지 않는다
        assertThat(result.active()).isTrue();
        // 표시명·설치 지점은 이전 기수 값을 그대로 잇는다
        assertThat(result.displayName()).isEqualTo("실습실 센서");
    }

    @Test
    void rejectsClaimingSensorOwnedByAnotherCohort() {
        SensorDevice owned = device(OTHER_SPACE_ID);
        when(spaceCohortQueryService.findCohortId(SPACE_ID)).thenReturn(Optional.of(COHORT_ID));
        when(sensorDeviceRepository.findByDeviceEui(DEVICE_EUI)).thenReturn(Optional.of(owned));
        when(spaceCohortQueryService.findCohortId(OTHER_SPACE_ID)).thenReturn(Optional.of(999L));

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                sensorDeviceService.claim(COHORT_ID, REQUESTER_ID, DEVICE_EUI, SPACE_ID)
        );

        // 남의 기수 것이라는 사실도 숨긴다 — 403이면 다른 기수 구성을 훑을 수 있다
        assertThat(thrown.getErrorCode()).isEqualTo(SensorErrorCode.DEVICE_NOT_FOUND);
        verify(sensorDeviceRepository, never()).save(any());
    }

    @Test
    void rejectsClaimIntoSpaceOfAnotherCohort() {
        when(spaceCohortQueryService.findCohortId(SPACE_ID)).thenReturn(Optional.of(999L));

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                sensorDeviceService.claim(COHORT_ID, REQUESTER_ID, DEVICE_EUI, SPACE_ID)
        );

        assertThat(thrown.getErrorCode()).isEqualTo(SensorErrorCode.DEVICE_SPACE_NOT_FOUND);
        // 공간 검사가 기기 조회보다 먼저다 — 남의 기수 공간을 찍어 EUI 존재를 떠볼 수 없다
        verify(sensorDeviceRepository, never()).findByDeviceEui(any());
    }

    private CreateSensorDeviceCommand createCommand() {
        return new CreateSensorDeviceCommand(
                SPACE_ID,
                DEVICE_EUI,
                "AM103",
                "실습실 센서",
                "창가",
                60,
                INSTALLED_AT
        );
    }

    private UpdateSensorDeviceCommand updateCommand() {
        return new UpdateSensorDeviceCommand(
                SPACE_ID,
                "수정된 센서",
                "출입문",
                120,
                INSTALLED_AT
        );
    }

    private SensorDevice device(Long spaceId) {
        return SensorDevice.create(
                DEVICE_EUI,
                spaceId,
                "AM103",
                "실습실 센서",
                "창가",
                60,
                INSTALLED_AT
        );
    }
}
