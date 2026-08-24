package site.omagotchi.learningservice.global.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalRole은 identity-service가 소유하고 gateway, learning-service, view가 복제한다.
 *
 * <p>네 저장소가 분리되어 컴파일러가 일치를 보장하지 못한다. identity에 역할이 추가되면
 * 나머지 세 서비스는 컴파일 오류도 테스트 실패도 없이 조용히 어긋난 상태가 되므로
 * 이 테스트가 계약을 고정해 갱신 누락을 빌드 시점에 드러낸다.
 */
class GlobalRoleContractTest {

    // identity-service의 GlobalRole 정의를 문자열로 고정한 것
    private static final List<String> IDENTITY_CONTRACT = List.of("USER", "SYSTEM_ADMIN");

    @Test
    @DisplayName("identity-service가 발급하는 역할과 동일한 값을 같은 순서로 가진다")
    void matchesIdentityServiceContract() {
        List<String> actual = Arrays.stream(GlobalRole.values())
                .map(Enum::name)
                .toList();

        assertThat(actual)
                .as("identity-service의 GlobalRole과 불일치. "
                        + "역할이 추가·변경되었다면 identity, gateway, learning-service, view를 "
                        + "모두 갱신해야 한다.")
                .isEqualTo(IDENTITY_CONTRACT);
    }

    @Test
    @DisplayName("알려지지 않은 역할 문자열을 거부한다")
    void rejectsUnknownRoleName() {
        // JWT Claim은 외부 입력이다. 미지의 값이 통과하면 권한 판단이 무너진다.
        assertThat(GlobalRole.isSupported("SUPER_ADMIN")).isFalse();
        assertThat(GlobalRole.isSupported("")).isFalse();
        assertThat(GlobalRole.isSupported(null)).isFalse();
        assertThat(GlobalRole.isSupported("user")).isFalse();
        assertThat(GlobalRole.isSupported(" USER")).isFalse();
    }
}
