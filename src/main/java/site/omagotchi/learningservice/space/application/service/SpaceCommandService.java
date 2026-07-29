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
import site.omagotchi.learningservice.space.application.port.out.SpaceRepository;
import site.omagotchi.learningservice.space.application.port.out.SpaceOccupancyQueryPort;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;

import java.time.Clock;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class SpaceCommandService
        implements CreateSpaceUseCase,
        ActivateSpaceUseCase,
        DeactivateSpaceUseCase,
        UpdateSpaceUseCase,
        DeleteSpaceUseCase {

    private final SpaceRepository spaceRepository;
    private final SpaceOccupancyQueryPort spaceOccupancyQueryPort;
    private final Clock clock;

    @Override
    public Space create(
            CreateSpaceCommand command
    ) {
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
                ZonedDateTime.now(clock)
        );

        return spaceRepository.save(space);
    }

    @Override
    public Space update(
            Long spaceId,
            UpdateSpaceCommand command
    ) {
        Space existingSpace = findSpace(spaceId);
        ensureNotDeleted(existingSpace);

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
    public void delete(Long spaceId) {
        Space existingSpace = findSpace(spaceId);
        ensureNotDeleted(existingSpace);

        ZonedDateTime now =
                ZonedDateTime.now(clock);

        Space deletedSpace =
                existingSpace.delete(now);

        ensureNoActiveOccupancy(spaceId, now);

        spaceRepository.save(deletedSpace);
    }

    @Override
    public Space activate(Long spaceId) {
        Space existingSpace = findSpace(spaceId);
        ZonedDateTime now = ZonedDateTime.now(clock);

        return spaceRepository.save(existingSpace.activate(now));
    }

    @Override
    public Space deactivate(
            Long spaceId,
            String reason
    ) {
        Space existingSpace = findSpace(spaceId);
        ZonedDateTime now = ZonedDateTime.now(clock);

        Space deactivatedSpace = existingSpace.deactivate(reason, now);

        ensureNoActiveOccupancy(spaceId, now);

        return spaceRepository.save(deactivatedSpace);
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

    private String normalizeName(
            String name
    ) {
        return name == null
                ? null
                : name.trim();
    }
}
