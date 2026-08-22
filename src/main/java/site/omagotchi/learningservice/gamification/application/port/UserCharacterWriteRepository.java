package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.domain.UserCharacter;

/**
 * 대표 캐릭터 생성 저장 경계
 * <p>대표 닉네임은 부분 유니크 인덱스로 보호된다. 사전 조회와 저장 사이에 경합이 나면
 * 저장 시점은 위반이 발생하므로, 그 변환을 저장소 구현이 책임진다.
 * Application은 Hibernate 예외 계층과 인덱스명을 알 필요가 없다.
 */
public interface UserCharacterWriteRepository {
    /**
     * 대표 캐릭터를 저장한다.
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     * 닉네임이 이미 사용중이면 DUPLICATE_NICKNAME
     */
    UserCharacter saveRepresentative(UserCharacter userCharacter);
}
