package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.SpaceCohortAccessPort;
import site.omagotchi.learningservice.space.application.port.SpaceOccupancyQueryPort;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SpaceCommandService {

    private static final int MAX_NAME_LENGTH = 50;

    private final SpaceRepository spaceRepository;
    private final SpaceOccupancyQueryPort spaceOccupancyQueryPort;
    private final SpaceCohortAccessPort cohortAccessPort;
    private final Clock clock;

    public Space create(
            CreateSpaceCommand command,
            UUID actorUserId,
            GlobalRole globalRole
    ) {
        Long cohortId = resolveCreationCohortId(
                command.cohortId(),
                actorUserId,
                globalRole
        );

        String normalizedName =
                normalizeName(command.name());

        if (normalizedName != null
                && spaceRepository.existsActiveByName(
                normalizedName
        )) {
            throw new BusinessException(SpaceErrorCode.DUPLICATE_NAME);
        }

        validateSpaceAttributes(
                normalizedName,
                command.spaceType(),
                command.capacity()
        );

        Space space = Space.create(
                normalizedName,
                command.spaceType(),
                command.capacity(),
                cohortId,
                ZonedDateTime.now(clock)
        );

        return spaceRepository.save(space);
    }

    public Space update(
            Long spaceId,
            UpdateSpaceCommand command,
            UUID actorUserId,
            GlobalRole globalRole
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, globalRole, false);

        String normalizedName =
                normalizeName(command.name());

        boolean duplicateName =
                normalizedName != null
                        && spaceRepository
                        .existsActiveByNameAndIdNot(
                                normalizedName,
                                spaceId
                        );

        if (duplicateName) {
            throw new BusinessException(SpaceErrorCode.DUPLICATE_NAME);
        }

        validateSpaceAttributes(
                normalizedName,
                command.spaceType(),
                command.capacity()
        );

        ZonedDateTime now =
                ZonedDateTime.now(clock);

        boolean changesType = existingSpace.getSpaceType()
                != command.spaceType();

        if (changesType && existingSpace.isActive()) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_TYPE_CHANGE_NOT_ALLOWED
            );
        }

        if (changesType && existingSpace.isAssignedLab()) {
            throw new BusinessException(
                    SpaceErrorCode.ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED
            );
        }

        if (command.capacity() < existingSpace.getCapacity()
                && existingSpace.isActive()) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED
            );
        }

        Space updatedSpace = existingSpace
                .changeName(
                        normalizedName,
                        now
                )
                .changeType(
                        command.spaceType(),
                        now
                )
                .changeCapacity(
                        command.capacity(),
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
            UUID actorUserId,
            GlobalRole globalRole
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, globalRole, true);

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
            UUID actorUserId,
            GlobalRole globalRole
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, globalRole, false);
        ZonedDateTime now = ZonedDateTime.now(clock);

        if (existingSpace.isActive()) {
            throw new BusinessException(SpaceErrorCode.ALREADY_ACTIVE);
        }

        return spaceRepository.save(existingSpace.activate(now));
    }

    public Space deactivate(
            Long spaceId,
            String reason,
            UUID actorUserId,
            GlobalRole globalRole
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, globalRole, false);
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
            UUID actorUserId,
            GlobalRole globalRole
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);
        requireExistingCohort(cohortId);

        if (existingSpace.getCohortId() == null) {
            requireCohortManager(cohortId, actorUserId, globalRole);
        } else {
            requireCohortManager(
                    existingSpace.getCohortId(),
                    actorUserId,
                    globalRole
            );

            if (!existingSpace.getCohortId().equals(cohortId)) {
                requireCohortManager(cohortId, actorUserId, globalRole);
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
            UUID actorUserId,
            GlobalRole globalRole
    ) {
        Space existingSpace = findSpaceForUpdate(spaceId);
        ensureNotDeleted(existingSpace);

        if (existingSpace.getCohortId() == null) {
            if (!cohortAccessPort.isSystemAdmin(globalRole)) {
                throw new BusinessException(SpaceErrorCode.ACCESS_DENIED);
            }
        } else {
            requireCohortManager(
                    existingSpace.getCohortId(),
                    actorUserId,
                    globalRole
            );
        }

        ensureLab(existingSpace);

        if (existingSpace.getCohortId() == null) {
            throw new BusinessException(SpaceErrorCode.LAB_NOT_ASSIGNED);
        }

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
            throw new BusinessException(SpaceErrorCode.DELETED_SPACE);
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
            GlobalRole globalRole,
            boolean deleteCommand
    ) {
        if (space.getCohortId() == null) {
            if (deleteCommand) {
                throw new BusinessException(
                        SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED
                );
            }

            if (!cohortAccessPort.isSystemAdmin(globalRole)) {
                throw new BusinessException(SpaceErrorCode.ACCESS_DENIED);
            }

            return;
        }

        requireCohortManager(
                space.getCohortId(), actorUserId, globalRole
        );
    }

    private Long resolveCreationCohortId(
            Long requestedCohortId,
            UUID actorUserId,
            GlobalRole globalRole
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
                actorUserId,
                globalRole
        );
        return requestedCohortId;
    }

    private void requireCohortManager(
            Long cohortId,
            UUID actorUserId,
            GlobalRole globalRole
    ) {
        if (cohortAccessPort.isSystemAdmin(globalRole)) {
            return;
        }

        if (!cohortAccessPort.isActiveManager(cohortId, actorUserId)) {
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

    private void validateSpaceAttributes(
            String name,
            SpaceType spaceType,
            Integer capacity
    ) {
        if (name == null || name.isBlank()
                || name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(SpaceErrorCode.INVALID_NAME);
        }

        if (spaceType == null) {
            throw new BusinessException(SpaceErrorCode.INVALID_TYPE);
        }

        if (capacity == null || capacity <= 0) {
            throw new BusinessException(SpaceErrorCode.INVALID_CAPACITY);
        }
    }

    private String normalizeName(
            String name
    ) {
        return name == null
                ? null
                : name.trim();
    }
}
