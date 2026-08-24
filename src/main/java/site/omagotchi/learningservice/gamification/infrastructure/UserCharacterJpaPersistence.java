package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.GamificationErrorCode;
import site.omagotchi.learningservice.gamification.application.port.UserCharacterQueryRepository;
import site.omagotchi.learningservice.gamification.application.port.UserCharacterWriteRepository;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 대표 캐릭터 조회·쓰기의 JPA 구현.
 *
 * <p>"대표 캐릭터" 조건과 정렬, 비관적 잠금, 유니크 위반 변환을 이 계층 안에서만 처리한다.
 * Spring Data 메서드 이름 규칙과 Hibernate 예외 계층은 전부 기술 정보이므로
 * application이 알 필요가 없다.
 */
@Repository
@RequiredArgsConstructor
public class UserCharacterJpaPersistence
        implements UserCharacterQueryRepository, UserCharacterWriteRepository {

    private final UserCharacterRepository userCharacterRepository;

    @Override
    public Optional<UserCharacter> findRepresentativeByUserId(UUID userId) {
        return userCharacterRepository.findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(userId);
    }

    @Override
    public List<UserCharacter> findRepresentativesByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userCharacterRepository.findByUserIdInAndRepresentativeTrue(userIds);
    }

    @Override
    public boolean existsRepresentativeByUserId(UUID userId) {
        return userCharacterRepository.existsByUserIdAndRepresentativeTrue(userId);
    }

    @Override
    public boolean existsRepresentativeByNickname(String nickname) {
        return userCharacterRepository.existsByNicknameIgnoreCaseAndRepresentativeTrue(nickname);
    }

    @Override
    public boolean existsRepresentativeByNicknameExcludingId(
            String nickname,
            Long excludedUserCharacterId
    ) {
        return userCharacterRepository.existsByNicknameIgnoreCaseAndRepresentativeTrueAndIdNot(
                nickname,
                excludedUserCharacterId
        );
    }

    @Override
    public UserCharacter getForUpdate(Long userCharacterId) {
        return userCharacterRepository.findWithLockById(userCharacterId)
                .orElseThrow(() -> new BusinessException(
                        GamificationErrorCode.REPRESENTATIVE_CHARACTER_NOT_FOUND
                ));
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code save}가 아니라 {@code saveAndFlush}인 것이 핵심이다. flush가 커밋 시점으로
     * 밀리면 유니크 위반이 이 메서드 밖에서 터져 아래 catch에 걸리지 않고 500이 된다.
     */
    @Override
    public UserCharacter saveRepresentative(UserCharacter userCharacter) {
        return writeAndTranslate(userCharacter);
    }

    /**
     * {@inheritDoc}
     *
     * <p>영속 상태 Entity에 대한 {@code saveAndFlush}는 변경 내용을 즉시 UPDATE로 내보낸다.
     * 생성과 변경의 의미가 달라 메서드를 나누었을 뿐, 위반 변환 방식은 동일하다.
     */
    @Override
    public UserCharacter flushRepresentative(UserCharacter userCharacter) {
        return writeAndTranslate(userCharacter);
    }

    private UserCharacter writeAndTranslate(UserCharacter userCharacter) {
        try {
            return userCharacterRepository.saveAndFlush(userCharacter);
        } catch (DataIntegrityViolationException exception) {
            throw UserCharacterConstraintTranslator.translate(exception);
        }
    }
}
