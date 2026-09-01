package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortLockService;
import site.omagotchi.learningservice.cohort.application.result.CohortLockView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.OccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.SpaceLabReductionQueryPort;
import site.omagotchi.learningservice.space.application.port.SpaceReferenceQueryPort;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.application.result.SpaceLabReductionView;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceAttributes;
import site.omagotchi.learningservice.space.domain.SpaceStateTransitionException;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceValidationException;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SpaceCommandService {

    private final SpaceRepository spaceRepository;
    private final SpaceLabReductionQueryPort spaceLabReductionQueryPort;
    private final OccupancyQueryService occupancyQueryService;
    private final VacancyAlertService vacancyAlertService;
    private final CohortAccessService cohortAccessService;
    private final SpaceReferenceQueryPort spaceReferenceQueryPort;
    private final CohortLockService cohortLockService;
    private final SpacePresenceQueryService spacePresenceQueryService;
    private final Clock clock;

    public Space create(
            CreateSpaceCommand command,
            UUID actorUserId
    ) {
        Long cohortId = resolveCreationCohortId(
                command.cohortId(),
                actorUserId
        );

        SpaceAttributes attributes = validateSpaceAttributes(
                command.name(),
                command.spaceType(),
                command.capacity()
        );

        if (spaceRepository.existsActiveByName(attributes.name())) {
            throw new BusinessException(SpaceErrorCode.DUPLICATE_NAME);
        }

        Space space = Space.create(
                attributes.name(),
                attributes.spaceType(),
                attributes.capacity(),
                cohortId,
                ZonedDateTime.now(clock)
        );

        return spaceRepository.save(space);
    }

    public Space update(
            Long spaceId,
            UpdateSpaceCommand command,
            UUID actorUserId
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, false);

        SpaceAttributes attributes = validateSpaceAttributes(
                command.name(),
                command.spaceType(),
                command.capacity()
        );

        boolean duplicateName =
                spaceRepository
                        .existsActiveByNameAndIdNot(
                                attributes.name(),
                                spaceId
                        );

        if (duplicateName) {
            throw new BusinessException(SpaceErrorCode.DUPLICATE_NAME);
        }

        ZonedDateTime now =
                ZonedDateTime.now(clock);

        boolean changesType = existingSpace.getSpaceType()
                != attributes.spaceType();

        // 상태가 막는 변경(RM-14)은 Domain이 스스로 검사한다. 여기서 같은 조건을 미리 보면
        // 불변식이 두 곳에 생겨 한쪽만 바뀌므로, 사유만 받아 외부 오류로 옮긴다.
        Space updatedSpace = applyAttributes(existingSpace, attributes, now);

        if (changesType) {
            ensureNoActiveOccupancy(spaceId, now);
        }

        return spaceRepository.save(
                updatedSpace
        );
    }

    public void delete(
            Long spaceId,
            UUID actorUserId
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, true);

        ZonedDateTime now =
                ZonedDateTime.now(clock);

        if (existingSpace.isActive()) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_SPACE_DELETE_NOT_ALLOWED
            );
        }

        ensureNoActiveOccupancy(spaceId, now);
        ensureNoSensorPlaced(spaceId);

        Space deletedSpace =
                existingSpace.delete(now);

        spaceRepository.save(deletedSpace);
    }

    public Space activate(
            Long spaceId,
            UUID actorUserId
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, false);
        ZonedDateTime now = ZonedDateTime.now(clock);

        if (existingSpace.isActive()) {
            throw new BusinessException(SpaceErrorCode.ALREADY_ACTIVE);
        }

        return spaceRepository.save(existingSpace.activate(now));
    }

    public Space deactivate(
            Long spaceId,
            String reason,
            UUID actorUserId
    ) {
        LockedSpaceContext context = lockSpaceWithLabReductionCohort(
                spaceId,
                actorUserId,
                true
        );
        Space existingSpace = context.space();
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, false);
        ZonedDateTime now = ZonedDateTime.now(clock);

        if (existingSpace.isInactive()) {
            throw new BusinessException(SpaceErrorCode.ALREADY_INACTIVE);
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    SpaceErrorCode.INVALID_INACTIVE_REASON
            );
        }

        ensureActiveLabRemains(existingSpace, context.cohort());
        ensureNoActiveOccupancy(spaceId, now);
        ensureNoCurrentPresenceOrReturnReservation(existingSpace);

        Space deactivatedSpace = existingSpace.deactivate(reason, now);
        Space saved = spaceRepository.save(deactivatedSpace);

        // 대기 신청 정리를 같은 Transaction에 두는 것이 명세 04 §2가 정한 것이다. 나누면
        // 비활성 공간에 신청이 남아, 다시 활성화될 때까지 아무 일도 일어나지 않는 신청이
        // 된다. 대기 중 알림은 비활성화를 막지 않는다 (RM-12) — 클릭 한 번의 의사표시가
        // 관리 행위를 무력화하지 않도록 정리 대상으로만 취급한다.
        vacancyAlertService.discardBySpace(spaceId);

        return saved;
    }

    public Space assignCohort(
            Long spaceId,
            Long cohortId,
            UUID actorUserId
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        // 유형은 검증하지 않는다 — 배정 대상은 전 유형이다 (ADR 0016).
        //
        // 기존 배정 기수의 매니저인지는 묻지 않는다. 미배정 공간은 기수 매니저 누구나
        // 배정할 수 있고(RM-16), 이미 배정된 공간이면 대상 기수 매니저인 요청자는 결과가
        // 같다 — "이미 다른 기수에 배정됨"(409)이다. 소유 기수 권한을 먼저 보면 같은 상황이
        // 요청자에 따라 403과 409로 갈려, 클라이언트가 배정 가능 여부를 오해한다.
        requireExistingCohort(cohortId);
        requireCohortManager(cohortId, actorUserId);

        if (existingSpace.getCohortId() != null) {
            throw new BusinessException(SpaceErrorCode.SPACE_ALREADY_ASSIGNED);
        }

        return spaceRepository.save(existingSpace.assignCohort(
                cohortId,
                ZonedDateTime.now(clock)
        ));
    }

    public Space unassignCohort(
            Long spaceId,
            UUID actorUserId
    ) {
        LockedSpaceContext context = lockSpaceWithLabReductionCohort(
                spaceId,
                actorUserId,
                false
        );
        Space existingSpace = context.space();
        ensureNotDeleted(existingSpace);

        if (existingSpace.getCohortId() == null) {
            throw new BusinessException(SpaceErrorCode.SPACE_NOT_ASSIGNED);
        }

        requireCohortManager(existingSpace.getCohortId(), actorUserId);

        ZonedDateTime now = ZonedDateTime.now(clock);
        ensureActiveLabRemains(existingSpace, context.cohort());
        // 체류 writer가 완전히 연동되기 전에도 진행 중 회의의 관리 주체가 사라지지 않게 한다.
        // 연동 후에는 현재 체류 검사와 겹치지만, 점유 자체의 보존 계약을 독립적으로 지킨다.
        ensureNoActiveOccupancy(spaceId, now);
        ensureNoCurrentPresenceOrReturnReservation(existingSpace);

        return spaceRepository.save(existingSpace.unassignCohort(
                now
        ));
    }

    /**
     * 활성 LAB을 줄이는 명령은 기수 행을 먼저, 공간 행을 나중에 잠근다.
     *
     * <p>첫 조회는 잠글 기수를 결정하는 스냅샷일 뿐이다. 공간 잠금 뒤 배정이 달라졌다면
     * 역순 잠금을 시도하지 않고 409로 재시도를 요청한다.</p>
     */
    private LockedSpaceContext lockSpaceWithLabReductionCohort(
            Long spaceId,
            UUID actorUserId,
            boolean requireManagerForUnassignedSpace
    ) {
        SpaceLabReductionView snapshot = spaceLabReductionQueryPort.find(spaceId)
                .orElseThrow(() -> new BusinessException(SpaceErrorCode.NOT_FOUND));

        // 권한이 없는 요청이 공간 전체가 공유하는 기수 행 잠금을 잡지 않게 먼저 거른다.
        // 잠금 뒤 실제 공간의 권한을 다시 확인하므로 이 조회는 최종 인가 판정이 아니다.
        if (snapshot.cohortId() != null) {
            requireCohortManager(snapshot.cohortId(), actorUserId);
        } else if (requireManagerForUnassignedSpace) {
            requireAnyCohortManager(actorUserId);
        }

        CohortLockView cohort = snapshot.activeLab() && snapshot.cohortId() != null
                ? cohortLockService.lock(snapshot.cohortId())
                : null;

        Space lockedSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(lockedSpace);

        if (isAssignedActiveLab(lockedSpace)
                && (cohort == null
                || !Objects.equals(cohort.cohortId(), lockedSpace.getCohortId()))) {
            throw new BusinessException(SpaceErrorCode.SPACE_STATE_CHANGED);
        }

        return new LockedSpaceContext(lockedSpace, cohort);
    }

    private Space findSpaceForUpdate(Long spaceId) {
        return spaceRepository
                .findByIdForUpdate(spaceId)
                .orElseThrow(() -> new BusinessException(
                        SpaceErrorCode.NOT_FOUND
                ));
    }

    private void ensureNotDeleted(Space space) {
        if (space.isDeleted()) {
            log.info("삭제된 공간에 대한 명령 요청 spaceId={}", space.getId());
            throw new BusinessException(SpaceErrorCode.NOT_FOUND);
        }
    }

    /**
     * 센서가 배치된 공간은 삭제하지 않는다.
     *
     * <p>소프트 삭제여도 {@code findCohortIdById}가 삭제된 공간을 걸러내므로, 그 공간의
     * 센서는 어느 기수에도 속하지 않게 된다. 그러면 목록에서 사라지고 수정·비활성화는
     * 기수 검사에 막히며 device_eui가 기본키라 재등록도 409로 거절된다. 매니저 인계가
     * 유일한 출구인데, 애초에 그 상태를 만들지 않는 편이 낫다.</p>
     */
    private void ensureNoSensorPlaced(Long spaceId) {
        if (spaceReferenceQueryPort.countSensors(spaceId) > 0) {
            throw new BusinessException(
                    SpaceErrorCode.SPACE_HAS_SENSOR_DELETE_NOT_ALLOWED
            );
        }
    }

    private void ensureNoActiveOccupancy(
            Long spaceId,
            ZonedDateTime now
    ) {
        if (occupancyQueryService.existsActive(spaceId, now.toOffsetDateTime())) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS
            );
        }
    }

    private void ensureActiveLabRemains(
            Space space,
            CohortLockView cohort
    ) {
        if (!isAssignedActiveLab(space) || cohort == null || !cohort.active()) {
            return;
        }

        if (spaceRepository.countActiveLabsByCohortId(space.getCohortId()) <= 1L) {
            throw new BusinessException(SpaceErrorCode.LAST_ACTIVE_LAB_REQUIRED);
        }
    }

    private void ensureNoCurrentPresenceOrReturnReservation(Space space) {
        SpacePresenceSummary summary = spacePresenceQueryService.summarize(space.getId());
        if (summary.currentCount() > 0L) {
            throw new BusinessException(SpaceErrorCode.SPACE_HAS_CURRENT_PRESENCE);
        }
        if (space.getSpaceType() == SpaceType.LAB
                && summary.returnReservationCount() > 0L) {
            throw new BusinessException(SpaceErrorCode.SPACE_HAS_RETURN_RESERVATION);
        }
    }

    private boolean isAssignedActiveLab(Space space) {
        return space.getCohortId() != null
                && space.getSpaceType() == SpaceType.LAB
                && space.isActive();
    }

    private void requireSpaceManager(
            Space space,
            UUID actorUserId,
            boolean deleteCommand
    ) {
        if (space.getCohortId() == null) {
            if (deleteCommand) {
                throw new BusinessException(
                        SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED
                );
            }

            requireAnyCohortManager(actorUserId);
            return;
        }

        requireCohortManager(
                space.getCohortId(), actorUserId
        );
    }

    private Long resolveCreationCohortId(
            Long requestedCohortId,
            UUID actorUserId
    ) {
        if (requestedCohortId == null) {
            List<Long> managedCohortIds = cohortAccessService
                    .findActiveManagedCohortIds(actorUserId);

            if (managedCohortIds.isEmpty()) {
                throw new BusinessException(
                        SpaceErrorCode.ACTIVE_COHORT_NOT_FOUND
                );
            }

            if (managedCohortIds.size() > 1) {
                throw new BusinessException(
                        SpaceErrorCode.COHORT_ID_REQUIRED
                );
            }

            return managedCohortIds.getFirst();
        }

        requireExistingCohort(requestedCohortId);
        requireCohortManager(
                requestedCohortId,
                actorUserId
        );
        return requestedCohortId;
    }

    private void requireCohortManager(
            Long cohortId,
            UUID actorUserId
    ) {
        if (!cohortAccessService.isManager(cohortId, actorUserId)) {
            throw new BusinessException(SpaceErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 관리 주체 기수가 없는 공간(시드·미배정, 유형 무관)의 관리 권한.
     *
     * <p>소유 기수가 없어 권한을 좁힐 수단이 없으므로 기수 매니저 누구나 수정·활성화·비활성화할
     * 수 있다 (RM-16). 되돌릴 수 없는 삭제만 소유 기반으로 막는다 (RM-25) — 호출부가
     * {@code deleteCommand}로 먼저 걸러낸다.</p>
     *
     * <p>시스템 관리자에게 예외를 주지 않는다. RM-17이 미수용이며
     * <b>"시스템 관리자는 기수 내의 일에 관여할 수 없음"</b>으로 확정됐다 (명세 01 §4).</p>
     */
    private void requireAnyCohortManager(UUID actorUserId) {
        if (cohortAccessService.findActiveManagedCohortIds(actorUserId).isEmpty()) {
            throw new BusinessException(SpaceErrorCode.ACCESS_DENIED);
        }
    }

    private void requireExistingCohort(Long cohortId) {
        if (cohortId == null || cohortId <= 0) {
            throw new BusinessException(SpaceErrorCode.INVALID_COHORT_ID);
        }

        if (!cohortAccessService.exists(cohortId)) {
            throw new BusinessException(SpaceErrorCode.COHORT_NOT_FOUND);
        }
    }

    /**
     * 이름·유형·정원을 한 번에 반영하고, 상태가 막은 변경을 외부 오류로 옮긴다.
     *
     * <p>거절 사유는 {@link SpaceStateTransitionException.Rule}로 구분한다 — Domain은 어떤
     * {@code ErrorCode}로 응답할지 모르고, 그 매핑이 이 Application의 책임이다.</p>
     *
     * <p>{@code cause}를 넘겨 원본을 보존한다. 예상 가능한 {@code 4xx}라 stack trace를 남기지는
     * 않지만, 조사 시 어느 규칙이 어디서 걸렸는지 추적할 수 있어야 한다.</p>
     */
    private Space applyAttributes(
            Space space,
            SpaceAttributes attributes,
            ZonedDateTime now
    ) {
        try {
            return space
                    .changeName(attributes.name(), now)
                    .changeType(attributes.spaceType(), now)
                    .changeCapacity(attributes.capacity(), now);
        } catch (SpaceStateTransitionException exception) {
            throw new BusinessException(toErrorCode(exception.violated()), exception);
        }
    }

    private static SpaceErrorCode toErrorCode(
            SpaceStateTransitionException.Rule violated
    ) {
        return switch (violated) {
            case ACTIVE_TYPE_CHANGE ->
                    SpaceErrorCode.ACTIVE_TYPE_CHANGE_NOT_ALLOWED;
            case ACTIVE_CAPACITY_REDUCTION ->
                    SpaceErrorCode.ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED;
        };
    }

    private SpaceAttributes validateSpaceAttributes(
            String name,
            SpaceType spaceType,
            Integer capacity
    ) {
        try {
            return new SpaceAttributes(name, spaceType, capacity);
        } catch (SpaceValidationException exception) {
            SpaceErrorCode errorCode = switch (exception.attribute()) {
                case NAME -> SpaceErrorCode.INVALID_NAME;
                case TYPE -> SpaceErrorCode.INVALID_TYPE;
                case CAPACITY -> SpaceErrorCode.INVALID_CAPACITY;
            };
            throw new BusinessException(errorCode, exception);
        }
    }

    private record LockedSpaceContext(
            Space space,
            CohortLockView cohort
    ) {
    }
}
