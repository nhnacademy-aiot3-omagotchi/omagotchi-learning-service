package site.omagotchi.learningservice.team.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.*;

class TeamTest {

    @Test
    @DisplayName("앞뒤 공백 제거")
    void test1() {
        assertThat(Team.normalizeName("    오마고치    ")).isEqualTo("오마고치");
    }

    @Test
    @DisplayName("공백만 있는 이름은 거부")
    void test2() {
        String name = " ";
        assertThatThrownBy(() -> Team.normalizeName(name))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.INVALID_NAME);
    }

    @Test
    @DisplayName("30자 초과 거부")
    void test3() {
        assertThatThrownBy(() -> Team.normalizeName("가".repeat(31)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("공백 포함 30자 초과 허용")
    void test4() {
        String name = "가".repeat(29) + " ";
        assertThat(Team.normalizeName(name)).hasSize(29);
    }

}
