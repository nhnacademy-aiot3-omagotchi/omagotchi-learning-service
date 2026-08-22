package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.domain.UserCharacter;

/**
 * 대표 캐릭터 쓰기 경계.
 *
 * <p>대표 닉네임은 부분 유니크 인덱스로 보호된다. 사전 조회와 저장 사이에 경합이 나면
 * 저장 시점에 위반이 발생하므로, 그 변환을 저장소 구현이 책임진다.
 * Application은 Hibernate 예외 계층과 인덱스명을 알 필요가 없다.
 *
 * <p>두 메서드 모두 DB 반영을 <b>호출 시점으로 당긴다</b>. 반영이 커밋 시점으로 밀리면
 * 유니크 위반이 서비스 트랜잭션 밖에서 터져 업무 오류로 변환할 기회가 사라진다.
 */
public interface UserCharacterWriteRepository {

    /**
     * 새 대표 캐릭터를 저장한다.
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         닉네임이 이미 사용 중이면 DUPLICATE_NICKNAME
     */
    UserCharacter saveRepresentative(UserCharacter userCharacter);

    /**
     * 이미 영속 상태인 대표 캐릭터의 변경을 즉시 DB에 반영한다.
     *
     * <p>닉네임 변경은 Dirty Checking으로 처리되어 UPDATE가 커밋 시점에 나간다.
     * 그 시점은 서비스 메서드 밖이므로 유니크 위반을 잡을 수 없다. 이 메서드로 반영을
     * 앞당겨 서비스 트랜잭션 경계 안에서 변환한다.
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         닉네임이 이미 사용 중이면 DUPLICATE_NICKNAME
     */
    UserCharacter flushRepresentative(UserCharacter userCharacter);
}
