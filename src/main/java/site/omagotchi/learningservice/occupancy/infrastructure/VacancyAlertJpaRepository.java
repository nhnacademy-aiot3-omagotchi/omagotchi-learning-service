package site.omagotchi.learningservice.occupancy.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VacancyAlertJpaRepository extends JpaRepository<VacancyAlert, Long> {

    /**
     * 대기 중 신청 목록.
     *
     * <p>{@code created_at} 정렬이 계약의 일부다 — 화면이 신청 순서로 보여주며,
     * 정렬이 없으면 같은 목록이 호출마다 달라 보인다.</p>
     */
    @Query("""
                SELECT a
                  FROM VacancyAlert a
                 WHERE a.cohortMembershipId IN :membershipIds
                   AND a.notifiedAt IS NULL
                 ORDER BY a.createdAt ASC, a.id ASC""")
    List<VacancyAlert> findWaitingByMembershipIds(
            @Param("membershipIds") Collection<Long> membershipIds
    );

    /** 값으로만 읽어 1차 캐시를 오염시키지 않는다 — 뒤이은 {@code FOR UPDATE}가 낡은 상태를 보게 된다. */
    @Query("""
                SELECT new site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository$WaitingAlert(
                       a.id, a.cohortMembershipId)
                  FROM VacancyAlert a
                 WHERE a.spaceId = :spaceId
                   AND a.notifiedAt IS NULL
                 ORDER BY a.createdAt ASC, a.id ASC""")
    List<VacancyAlertRepository.WaitingAlert> findWaitingBySpaceId(@Param("spaceId") Long spaceId);

    /**
     * 대기 중인 내 신청 삭제.
     *
     * <p>세 조건이 한 문장에 있는 것이 요점이다. 읽고 나서 지우면 그 사이 발송이 끝나도
     * 삭제가 그대로 진행돼 이력이 사라진다 — 조건이 붙어 있어야 삭제가 발송 커밋을
     * 기다린 뒤 {@code notified_at}을 다시 보고 0행으로 끝난다.</p>
     *
     * <p>벌크 삭제는 영속성 Context를 우회하지만, 취소 경로는 이 신청을 엔티티로 읽지
     * 않으므로 {@code clearAutomatically}가 필요 없다.</p>
     */
    @Modifying
    @Query("""
                DELETE FROM VacancyAlert a
                 WHERE a.id = :id
                   AND a.notifiedAt IS NULL
                   AND a.cohortMembershipId IN :membershipIds""")
    int deleteWaiting(
            @Param("id") Long id,
            @Param("membershipIds") Collection<Long> membershipIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                SELECT a
                  FROM VacancyAlert a
                 WHERE a.id = :id
                   AND a.notifiedAt IS NULL""")
    Optional<VacancyAlert> lockWaitingById(@Param("id") Long id);
}
