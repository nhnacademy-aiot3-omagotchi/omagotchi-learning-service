package site.omagotchi.learningservice.team.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.domain.TeamErrorCode;

import java.time.OffsetDateTime;

/**
 * 랭킹·게이미피케이션의 사회적 단위. 공간 접근과 무관하다.
 *
 * 기수 스코프이며 이름 유니크도 기수 내에서만 성립한다(GR-21).
 * cohort_id를 갖는 유일한 이유가 이 부분 유니크 인덱스다 — 인덱스는 조인할 수 없기 때문.
 * updated_at과 생성자 컬럼이 없는 것은 의도다 — 팀 수정 기능이 없고,
 * 권한 판정은 team_members.role, 자동 위임은 joined_at 기준이라 생성자를 쓰는 로직이 없다.
 */
@Entity
@Table(name = "teams", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    public static final int NAME_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public static Team create(Long cohortId, String rawName) {
        Team team = new Team();
        team.cohortId = cohortId;
        team.name = normalizeName(rawName);
        team.createdAt = OffsetDateTime.now();
        return team;
    }

    /**
     * 팀 이름 정규화 및 검증 (GR-21).
     * ck_teams_name이 name = BTRIM(name)을 요구하므로 정규화를 건너뛰면 DB에서 거부된다.
     * 중복 검사 쿼리와 저장이 같은 기준을 쓰도록 이 메서드를 공유한다.
     */
    public static String normalizeName(String rawName) {
        String normalized = rawName == null ? "" : rawName.strip();
        if (normalized.isEmpty() || normalized.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(TeamErrorCode.INVALID_NAME);
        }
        return normalized;
    }

    /** 팀 해체 (GR-13 단독 탈퇴 / GR-19 해체 / CE-01 기수 종료). 행은 보존한다. */
    public void disband() {
        this.deletedAt = OffsetDateTime.now();
    }

    public boolean isDisbanded() {
        return deletedAt != null;
    }
}