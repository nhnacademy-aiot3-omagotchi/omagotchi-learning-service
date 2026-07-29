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

    /**
     * 팀 이름 최대 길이 (GR-21). trim <b>후</b> 길이 기준이다.
     * DB의 {@code ck_teams_name} CHECK(1~30)과 같은 값이어야 하며,
     * 어긋나면 앱은 통과시킨 이름을 DB가 거부해 500이 난다.
     */
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

    /**
     * 팀을 생성한다 (GR-01).
     *
     * <p>이름은 여기서 정규화하므로 호출부가 미리 trim할 필요가 없다.
     * 다만 중복 검사는 정규화된 값으로 해야 하므로, 서비스는 보통
     * {@link #normalizeName(String)}을 먼저 호출해 그 결과로 검사한 뒤 이 메서드를 부른다.</p>
     *
     * <p>이 메서드는 {@code teams} 행만 만든다. MASTER {@code team_members} 행 생성은
     * 서비스가 같은 트랜잭션에서 이어서 해야 한다 — 끊기면 아무도 해체할 수 없는
     * 마스터 없는 팀이 남는다.</p>
     *
     * @param cohortId 팀이 속할 기수. 요청자의 활성 기수임이 검증된 값이어야 한다 (RM-28)
     * @param rawName  정규화 전 이름
     * @throws site.omagotchi.learningservice.global.exception.BusinessException 이름이 trim 후 1~30자가 아니면
     */
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

    /**
     * 해체 여부. 해체된 팀은 조회·조작 모두 404다.
     *
     * <p>팀 행은 보존되므로 "존재한다"와 "살아 있다"가 다르다. 락을 잡은 뒤
     * 이 값을 다시 봐야 해체 커밋 직후 도착한 요청을 잡아낼 수 있다.</p>
     */
    public boolean isDisbanded() {
        return deletedAt != null;
    }
}