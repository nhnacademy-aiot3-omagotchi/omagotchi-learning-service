package site.omagotchi.learningservice.sensor.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.SpaceSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SpaceEnvironmentQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorReadingSnapshot;
import site.omagotchi.learningservice.sensor.application.result.SpaceEnvironmentResult;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("공간 현재 환경 서비스")
class SpaceEnvironmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:30:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long COHORT_ID = 1L;
    private static final Long LAB_ID = 10L;
    private static final Long MEETING_ID = 11L;
    private static final UUID REQUESTER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );

    @Mock
    private SensorDeviceRepository sensorDeviceRepository;

    @Mock
    private SpaceSeriesRepository spaceSeriesRepository;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private SpaceCohortQueryService spaceCohortQueryService;

    private SpaceEnvironmentService service;

    @BeforeEach
    void setUp() {
        service = new SpaceEnvironmentService(
                sensorDeviceRepository,
                spaceSeriesRepository,
                cohortAccessService,
                spaceCohortQueryService,
                Clock.fixed(NOW, SEOUL)
        );
    }

    @Test
    @DisplayName("한 공간에 센서가 여러 대면 항목별로 평균을 낸다")
    void averagesReadingsOfDevicesInSameSpace() {
        // given: 같은 실습실에 CO2 센서 두 대가 서로 다른 값을 보고했다
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices(
                device("0011223344556677", LAB_ID),
                device("8899aabbccddeeff", LAB_ID)
        );
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of(
                reading("0011223344556677", "co2", 600.0, "2026-09-04T10:28:00Z"),
                reading("8899aabbccddeeff", "co2", 700.0, "2026-09-04T10:29:00Z"),
                reading("0011223344556677", "temperature", 23.0, "2026-09-04T10:28:00Z"),
                reading("8899aabbccddeeff", "temperature", 24.0, "2026-09-04T10:29:00Z")
        ));

        // when
        List<SpaceEnvironmentResult> results = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID);

        // then
        assertEquals(1, results.size());
        SpaceEnvironmentResult lab = results.getFirst();
        assertEquals(LAB_ID, lab.spaceId());
        assertEquals(650.0, lab.co2());
        assertEquals(23.5, lab.temperature());
        // 아무 기기도 보고하지 않은 항목은 비운다
        assertNull(lab.humidity());
        // 측정 시각은 그 공간에서 가장 최근에 들어온 값의 시각이다
        assertEquals(Instant.parse("2026-09-04T10:29:00Z"), lab.measuredAt());
        assertEquals(2, lab.deviceCount());
    }

    @Test
    @DisplayName("항목마다 그 항목을 보고한 기기끼리만 평균 낸다")
    void averagesEachMeasurementOverItsOwnDevices() {
        // given: CO2만 재는 기기와 온습도만 재는 기기가 한 공간에 섞여 있다
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices(
                device("0011223344556677", LAB_ID),
                device("8899aabbccddeeff", LAB_ID)
        );
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of(
                reading("0011223344556677", "co2", 800.0, "2026-09-04T10:28:00Z"),
                reading("8899aabbccddeeff", "humidity", 45.0, "2026-09-04T10:29:00Z")
        ));

        SpaceEnvironmentResult lab = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID).getFirst();

        assertEquals(800.0, lab.co2());
        assertEquals(45.0, lab.humidity());
        assertNull(lab.temperature());
    }

    @Test
    @DisplayName("값이 없는 공간도 목록에 남긴다")
    void keepsSpacesWithoutReadings() {
        // given: 회의실에는 센서가 없다
        givenCohortSpaces(List.of(LAB_ID, MEETING_ID));
        givenActiveDevices(device("0011223344556677", LAB_ID));
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of(
                reading("0011223344556677", "co2", 612.0, "2026-09-04T10:29:00Z")
        ));

        List<SpaceEnvironmentResult> results = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID);

        // 화면이 spaceId로 이어붙이므로 빠뜨리면 무엇을 못 받았는지 알 수 없다
        assertEquals(List.of(LAB_ID, MEETING_ID), results.stream().map(SpaceEnvironmentResult::spaceId).toList());
        assertEquals(612.0, results.get(0).co2());
        assertNull(results.get(1).co2());
        assertNull(results.get(1).measuredAt());
        // 값이 없는 이유가 "센서가 없어서"인지 "아직 안 들어와서"인지 화면이 구분해야 한다
        assertEquals(1, results.get(0).deviceCount());
        assertEquals(0, results.get(1).deviceCount());
    }

    @Test
    @DisplayName("센서가 배치된 공간인데 값이 없으면 기기 수는 남긴다")
    void keepsDeviceCountWhenReadingsAreMissing() {
        // given: 센서는 있는데 최근 30분간 아무 값도 오지 않았다
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices(device("0011223344556677", LAB_ID));
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of());

        SpaceEnvironmentResult lab = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID).getFirst();

        assertNull(lab.co2());
        // 센서 미설치(0)와 구분된다 — 이쪽은 끊김이거나 아직 안 들어온 것이다
        assertEquals(1, lab.deviceCount());
    }

    @Test
    @DisplayName("기수에 배정되지 않은 공용 공간도 함께 본다")
    void includesSharedSpacesWithoutCohort() {
        // given: 회의실은 관리 주체 기수가 없어 배정 목록에서 빠진다
        when(spaceCohortQueryService.findSpaceIdsByCohortId(COHORT_ID)).thenReturn(List.of(LAB_ID));
        givenSharedSpaces(List.of(MEETING_ID));
        givenActiveDevices(
                device("0011223344556677", LAB_ID),
                device("8899aabbccddeeff", MEETING_ID)
        );
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of(
                reading("0011223344556677", "co2", 612.0, "2026-09-04T10:28:00Z"),
                reading("8899aabbccddeeff", "co2", 704.0, "2026-09-04T10:29:00Z")
        ));

        List<SpaceEnvironmentResult> results = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID);

        // 화면의 회의실 탭도 같은 응답으로 값을 채운다
        assertEquals(List.of(LAB_ID, MEETING_ID),
                results.stream().map(SpaceEnvironmentResult::spaceId).toList());
        assertEquals(704.0, results.get(1).co2());
    }

    @Test
    @DisplayName("조회 구간은 가장 느린 기기의 수집 주기 × 3까지 거슬러 본다")
    void asksBackAsFarAsTheSlowestDeviceNeeds() {
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices(
                device("0011223344556677", LAB_ID, 60),
                device("8899aabbccddeeff", LAB_ID, 600)
        );
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of());

        service.getCohortEnvironments(COHORT_ID, REQUESTER_ID);

        ArgumentCaptor<SpaceEnvironmentQuery> captor =
                ArgumentCaptor.forClass(SpaceEnvironmentQuery.class);
        verify(spaceSeriesRepository).findLatestReadings(captor.capture());
        SpaceEnvironmentQuery query = captor.getValue();

        // 10분 주기 기기가 있으면 30분까지 본다. 빠른 기기는 뒤에서 따로 걸러진다
        assertEquals(NOW.minus(Duration.ofMinutes(30)), query.from());
        assertEquals(NOW, query.to());
        assertEquals(Set.of("0011223344556677", "8899aabbccddeeff"), query.deviceEuis());
        assertEquals(List.of("co2", "temperature", "humidity"), query.measurement());
    }

    @Test
    @DisplayName("수집 주기 상한(2시간)인 기기는 6시간까지 거슬러 본다")
    void looksBackSixHoursForTheSlowestAllowedInterval() {
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices(device("0011223344556677", LAB_ID, 7_200));
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of());

        service.getCohortEnvironments(COHORT_ID, REQUESTER_ID);

        ArgumentCaptor<SpaceEnvironmentQuery> captor =
                ArgumentCaptor.forClass(SpaceEnvironmentQuery.class);
        verify(spaceSeriesRepository).findLatestReadings(captor.capture());
        assertEquals(NOW.minus(Duration.ofHours(6)), captor.getValue().from());
    }

    @Test
    @DisplayName("상한을 넘는 주기여도 조회 폭과 판정 기준이 어긋나지 않는다")
    void clampsIntervalsBeyondTheCapSoTheWindowAlwaysCoversThem() {
        // given: 도메인이 주기 상한을 검사하지 않아 3시간짜리 기기도 저장될 수 있다.
        // 기준만 9시간으로 늘리면 조회 폭(6시간)이 짧아 유효한 값이 먼저 잘려 나간다.
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices(device("0011223344556677", LAB_ID, 10_800));
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of(
                // 조회 폭을 벗어난 7시간 전 값. 실제로는 오지 않지만 와도 받아들이면 안 된다
                reading("0011223344556677", "co2", 400.0, "2026-09-04T03:30:00Z")
        ));

        SpaceEnvironmentResult lab = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID).getFirst();

        ArgumentCaptor<SpaceEnvironmentQuery> captor =
                ArgumentCaptor.forClass(SpaceEnvironmentQuery.class);
        verify(spaceSeriesRepository).findLatestReadings(captor.capture());

        // 조회 폭은 상한 주기 × 3 에서 멈추고, 판정도 같은 기준을 쓴다
        assertEquals(NOW.minus(Duration.ofHours(6)), captor.getValue().from());
        assertNull(lab.co2());
        assertEquals(1, lab.deviceCount());
    }

    @Test
    @DisplayName("자기 수집 주기 × 3을 넘긴 값은 현재 값으로 쓰지 않는다")
    void dropsReadingsOlderThanTheirOwnDeviceThreshold() {
        // given: 1분 주기 기기(임계 3분)와 10분 주기 기기(임계 30분)가 같은 공간에 있고
        // 둘 다 5분 전 값을 마지막으로 보냈다
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices(
                device("0011223344556677", LAB_ID, 60),
                device("8899aabbccddeeff", LAB_ID, 600)
        );
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of(
                reading("0011223344556677", "co2", 400.0, "2026-09-04T10:25:00Z"),
                reading("8899aabbccddeeff", "co2", 800.0, "2026-09-04T10:25:00Z")
        ));

        SpaceEnvironmentResult lab = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID).getFirst();

        // 빠른 기기 값은 이미 끊긴 것으로 보고 버린다 — Rule Service 의 끊김 판정과 같은 기준
        assertEquals(800.0, lab.co2());
        assertEquals(Instant.parse("2026-09-04T10:25:00Z"), lab.measuredAt());
        // 기기 수는 배치 기준이라 그대로다. 값이 하나뿐인 것과는 다른 정보다
        assertEquals(2, lab.deviceCount());
    }

    @Test
    @DisplayName("모든 기기가 기준을 넘기면 값 없이 기기 수만 남는다")
    void leavesSpaceEmptyWhenEveryDeviceIsStale() {
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices(device("0011223344556677", LAB_ID, 60));
        when(spaceSeriesRepository.findLatestReadings(any())).thenReturn(List.of(
                reading("0011223344556677", "co2", 400.0, "2026-09-04T10:20:00Z")
        ));

        SpaceEnvironmentResult lab = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID).getFirst();

        // 화면은 "센서 없음"이 아니라 "측정 대기"로 말해야 한다
        assertNull(lab.co2());
        assertNull(lab.measuredAt());
        assertEquals(1, lab.deviceCount());
    }

    @Test
    @DisplayName("등록된 기기가 없으면 시계열을 조회하지 않는다")
    void skipsLookupWhenCohortHasNoDevices() {
        givenCohortSpaces(List.of(LAB_ID));
        givenActiveDevices();

        List<SpaceEnvironmentResult> results = service.getCohortEnvironments(COHORT_ID, REQUESTER_ID);

        assertEquals(1, results.size());
        assertNull(results.getFirst().co2());
        assertEquals(0, results.getFirst().deviceCount());
        verifyNoInteractions(spaceSeriesRepository);
    }

    @Test
    @DisplayName("기수에 속하지 않으면 조회 전에 막힌다")
    void rejectsNonMember() {
        doThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND))
                .when(cohortAccessService).requireActiveMembershipId(COHORT_ID, REQUESTER_ID);

        assertThrows(BusinessException.class,
                () -> service.getCohortEnvironments(COHORT_ID, REQUESTER_ID));

        verifyNoInteractions(sensorDeviceRepository, spaceSeriesRepository, spaceCohortQueryService);
    }

    private void givenCohortSpaces(List<Long> spaceIds) {
        when(spaceCohortQueryService.findSpaceIdsByCohortId(COHORT_ID)).thenReturn(spaceIds);
        when(spaceCohortQueryService.findUnassignedSpaceIds()).thenReturn(List.of());
    }

    private void givenSharedSpaces(List<Long> spaceIds) {
        when(spaceCohortQueryService.findUnassignedSpaceIds()).thenReturn(spaceIds);
    }

    private void givenActiveDevices(SensorDevice... devices) {
        when(sensorDeviceRepository.findActiveBySpaceIds(any())).thenReturn(List.of(devices));
    }

    private static SensorDevice device(String deviceEui, Long spaceId) {
        return device(deviceEui, spaceId, 60);
    }

    private static SensorDevice device(String deviceEui, Long spaceId, int expectedIntervalSeconds) {
        return SensorDevice.create(
                deviceEui, spaceId, "CO2-01", "센서 " + deviceEui, null, expectedIntervalSeconds, NOW);
    }

    private static SensorReadingSnapshot reading(
            String deviceEui,
            String measurement,
            double value,
            String time
    ) {
        return new SensorReadingSnapshot(deviceEui, measurement, value, Instant.parse(time));
    }
}
