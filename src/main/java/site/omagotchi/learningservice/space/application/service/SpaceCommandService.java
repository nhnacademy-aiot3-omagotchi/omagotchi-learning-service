package site.omagotchi.learningservice.space.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.in.CreateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.ActivateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeactivateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeleteSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.UpdateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.AssignLabCohortUseCase;
import site.omagotchi.learningservice.space.application.port.in.UnassignLabCohortUseCase;
import site.omagotchi.learningservice.space.application.port.out.SpaceCohortAccessPort;
import site.omagotchi.learningservice.space.application.port.out.SpaceRepository;
import site.omagotchi.learningservice.space.application.port.out.SpaceOccupancyQueryPort;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SpaceCommandService
        implements CreateSpaceUseCase,
        ActivateSpaceUseCase,
        DeactivateSpaceUseCase,
        UpdateSpaceUseCase,
        DeleteSpaceUseCase,
        AssignLabCohortUseCase,
        UnassignLabCohortUseCase {

    private final SpaceRepository spaceRepository;
    private final SpaceOccupancyQueryPort spaceOccupancyQueryPort;
    private final SpaceCohortAccessPort cohortAccessPort;
    private final Clock clock;

    @Override
    public Space create(
            CreateSpaceCommand command,
            UUID actorUserId,
            String globalRole
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

        Space space = Space.create(
                normalizedName,
                command.spaceType(),
                command.capacity(),
                cohortId,
                ZonedDateTime.now(clock)
        );

        return spaceRepository.save(space);
    }

    @Override
    public Space update(
            Long spaceId,
            UpdateSpaceCommand command,
            UUID actorUserId,
            String globalRole
    ) {
        Space existingSpace = findSpace(spaceId);
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

        ZonedDateTime now =
                ZonedDateTime.now(clock);

        boolean changesType = existingSpace.getSpaceType()
                != command.spaceType();

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

    @Override
    public void delete(
            Long spaceId,
            UUID actorUserId,
            String globalRole
    ) {
        Space existingSpace = findSpace(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, globalRole, true);

        ZonedDateTime now =
                ZonedDateTime.now(clock);

        Space deletedSpace =
                existingSpace.delete(now);

        spaceRepository.save(deletedSpace);
    }

    @Override
    public Space activate(
            Long spaceId,
            UUID actorUserId,
            String globalRole
    ) {
        Space existingSpace = findSpace(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, globalRole, false);
        ZonedDateTime now = ZonedDateTime.now(clock);

        return spaceRepository.save(existingSpace.activate(now));
    }

    @Override
    public Space deactivate(
            Long spaceId,
            String reason,
            UUID actorUserId,
            String globalRole
    ) {
        Space existingSpace = findSpace(spaceId);
        ensureNotDeleted(existingSpace);
        requireSpaceManager(existingSpace, actorUserId, globalRole, false);
        ZonedDateTime now = ZonedDateTime.now(clock);

        Space deactivatedSpace = existingSpace.deactivate(reason, now);

        ensureNoActiveOccupancy(spaceId, now);

        return spaceRepository.save(deactivatedSpace);
    }

    @Override
    public Space assignCohort(
            Long spaceId,
            Long cohortId,
            UUID actorUserId,
            String globalRole
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

        return spaceRepository.save(existingSpace.assignCohort(
                cohortId,
                ZonedDateTime.now(clock)
        ));
    }

    @Override
    public Space unassignCohort(
            Long spaceId,
            UUID actorUserId,
            String globalRole
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

        return spaceRepository.save(existingSpace.unassignCohort(
                ZonedDateTime.now(clock)
        ));
    }

    private Space findSpace(
            Long spaceId
    ) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(
                        () -> new BusinessException(
                                SpaceErrorCode.NOT_FOUND
                        )
                );
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
            String globalRole,
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
            String globalRole
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
            String globalRole
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

    private String normalizeName(
            String name
    ) {
        return name == null
                ? null
                : name.trim();
    }
}
