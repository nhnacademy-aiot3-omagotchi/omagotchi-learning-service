package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.port.UserCharacterWriteRepository;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;

/**
 * 대표 캐릭터 저장의 JPA 구현.
 *
 * <p>유니크 위반을 업무 오류로 바꾸는 책임을 여기에 둔다. Hibernate 예외 계층과
 * DB 인덱스명은 전부 기술 정보이므로 application이 알 필요가 없다.
 */
@Repository
@RequiredArgsConstructor
public class UserCharacterJpaPersistence implements UserCharacterWriteRepository {

    private final UserCharacterRepository userCharacterRepository;

    /**
     * {@inheritDoc}
     *
     * <p>{@code save}가 아니라 {@code saveAndFlush}인 것이 핵심이다. flush가 커밋 시점으로
     * 밀리면 유니크 위반이 이 메서드 밖에서 터져 아래 catch에 걸리지 않고 500이 된다.
     */
    @Override
    public UserCharacter saveRepresentative(UserCharacter userCharacter) {
        try {
            return userCharacterRepository.saveAndFlush(userCharacter);
        } catch (DataIntegrityViolationException exception) {
            throw UserCharacterConstraintTranslator.translate(exception);
        }
    }
}
