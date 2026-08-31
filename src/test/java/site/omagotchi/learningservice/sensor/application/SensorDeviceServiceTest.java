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
    void scopesListToCohortSpacesAfterManagerCheck() {
        SensorDevice device = device(SPACE_ID);
        when(spaceCohortQueryService.findSpaceIdsByCohortId(COHORT_ID))
                .thenReturn(List.of(SPACE_ID));
        when(sensorDeviceRepository.findBySpaceIds(List.of(SPACE_ID)))
                .thenReturn(List.of(device));

        var results = sensorDeviceService.findAll(COHORT_ID, REQUESTER_ID);

        // 기기 마스터는 소속이 아니라 매니저다 — 소속으로 되돌아가면 여기서 걸린다
        verify(cohortAccessService).requireManager(COHORT_ID, REQUESTER_ID);
        assertThat(results)
                .extracting(result -> result.deviceEui())
                .containsExactly(DEVICE_EUI);
    }

    @Test
    void rejectsListingSensorMasterWhenRequesterIsNotManager() {
        doThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                .when(cohortAccessService)
                .requireManager(COHORT_ID, REQUESTER_ID);

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                sensorDeviceService.findAll(COHORT_ID, REQUESTER_ID)
        );

        assertThat(thrown.getErrorCode())
                .isEqualTo(CohortErrorCode.COHORT_MANAGER_REQUIRED);
        verifyNoInteractions(sensorDeviceRepository, spaceCohortQueryService);
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
