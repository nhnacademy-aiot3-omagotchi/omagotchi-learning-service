package site.omagotchi.learningservice.space.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.in.CreateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeleteSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.UpdateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.out.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.exception.DuplicateSpaceNameException;
import site.omagotchi.learningservice.space.domain.exception.SpaceNotFoundException;

import java.time.Clock;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class SpaceCommandService
        implements CreateSpaceUseCase,
        UpdateSpaceUseCase,
        DeleteSpaceUseCase {

    private final SpaceRepository spaceRepository;
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
            throw new DuplicateSpaceNameException();
        }

        Space space = Space.create(
                normalizedName,
                command.type(),
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
        Space existingSpace =
                findActiveSpace(spaceId);

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
            throw new DuplicateSpaceNameException();
        }

        ZonedDateTime now =
                ZonedDateTime.now(clock);

        Space updatedSpace = existingSpace
                .changeName(
                        normalizedName,
                        now
                )
                .changeType(
                        command.type(),
                        now
                )
                .changeCapacity(
                        command.capacity(),
                        now
                );

        return spaceRepository.save(
                updatedSpace
        );
    }

    @Override
    public void delete(Long spaceId) {
        Space existingSpace =
                findActiveSpace(spaceId);

        ZonedDateTime now =
                ZonedDateTime.now(clock);

        Space deletedSpace =
                existingSpace.delete(now);

        spaceRepository.save(deletedSpace);
    }

    private Space findActiveSpace(
            Long spaceId
    ) {
        return spaceRepository
                .findActiveById(spaceId)
                .orElseThrow(
                        SpaceNotFoundException::new
                );
    }

    private String normalizeName(
            String name
    ) {
        return name == null
                ? null
                : name.trim();
    }
}
