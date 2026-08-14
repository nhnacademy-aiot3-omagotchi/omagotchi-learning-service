package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.SpaceCohortAccessPort;
import site.omagotchi.learningservice.space.application.port.SpaceOccupancyQueryPort;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceAttributes;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceValidationException;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SpaceCommandService {

    private final SpaceRepository spaceRepository;
    private final SpaceOccupancyQueryPort spaceOccupancyQueryPort;
    private final SpaceCohortAccessPort cohortAccessPort;
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

        if (changesType && existingSpace.isActive()) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_TYPE_CHANGE_NOT_ALLOWED
            );
        }

        if (attributes.capacity() < existingSpace.getCapacity()
                && existingSpace.isActive()) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED
            );
        }

        Space updatedSpace = existingSpace
                .changeName(
                        attributes.name(),
                        now
                )
                .changeType(
                        attributes.spaceType(),
                        now
                )
                .changeCapacity(
                        attributes.capacity(),
                        now
                );

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
        Space existingSpace = findSpaceForUpdate(spaceId);
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

        ensureNoActiveOccupancy(spaceId, now);

        Space deactivatedSpace = existingSpace.deactivate(reason, now);

        return spaceRepository.save(deactivatedSpace);
    }

    public Space assignCohort(
            Long spaceId,
            Long cohortId,
            UUID actorUserId
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireExistingCohort(cohortId);

        if (existingSpace.getCohortId() == null) {
            requireCohortManager(cohortId, actorUserId);
        } else {
            requireCohortManager(
                    existingSpace.getCohortId(),
                    actorUserId
            );

            if (!existingSpace.getCohortId().equals(cohortId)) {
                requireCohortManager(cohortId, actorUserId);
            }
        }

        ensureLab(existingSpace);

        if (existingSpace.getCohortId() != null) {
            throw new BusinessException(SpaceErrorCode.LAB_ALREADY_ASSIGNED);
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
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);

        ensureLab(existingSpace);

        if (existingSpace.getCohortId() == null) {
            throw new BusinessException(SpaceErrorCode.LAB_NOT_ASSIGNED);
        }

        requireCohortManager(existingSpace.getCohortId(), actorUserId);

        return spaceRepository.save(existingSpace.unassignCohort(
                ZonedDateTime.now(clock)
        ));
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

    private void ensureNoActiveOccupancy(
            Long spaceId,
            ZonedDateTime now
    ) {
        if (spaceOccupancyQueryPort.existsActiveOccupancy(spaceId, now)) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS
            );
        }
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
            List<Long> managedCohortIds = cohortAccessPort
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
        if (!cohortAccessPort.isActiveManager(cohortId, actorUserId)) {
            throw new BusinessException(SpaceErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 관리 주체 기수가 없는 공간(시드·미배정 실습실)의 관리 권한.
     *
     * <p>소유 기수가 없어 권한을 좁힐 수단이 없으므로 기수 매니저 누구나 수정·활성화·비활성화할
     * 수 있다 (RM-16). 되돌릴 수 없는 삭제만 소유 기반으로 막는다 (RM-25) — 호출부가
     * {@code deleteCommand}로 먼저 걸러낸다.</p>
     *
     * <p>시스템 관리자에게 예외를 주지 않는다. RM-17이 미수용이며
     * <b>"시스템 관리자는 기수 내의 일에 관여할 수 없음"</b>으로 확정됐다 (명세 01 §4).</p>
     */
    private void requireAnyCohortManager(UUID actorUserId) {
        if (cohortAccessPort.findActiveManagedCohortIds(actorUserId).isEmpty()) {
            throw new BusinessException(SpaceErrorCode.ACCESS_DENIED);
        }
    }

    private void requireExistingCohort(Long cohortId) {
        if (cohortId == null || cohortId <= 0) {
            throw new BusinessException(SpaceErrorCode.INVALID_COHORT_ID);
        }

        if (!cohortAccessPort.exists(cohortId)) {
            throw new BusinessException(SpaceErrorCode.COHORT_NOT_FOUND);
        }
    }

    private void ensureLab(Space space) {
        if (space.getSpaceType() != SpaceType.LAB) {
            throw new BusinessException(
                    SpaceErrorCode.LAB_ONLY_COHORT_ASSIGNMENT
            );
        }
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
}
