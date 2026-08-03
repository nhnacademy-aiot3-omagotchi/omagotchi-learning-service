package site.omagotchi.learningservice.space.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceRepositoryQueryContractTest {

    @Test
    void spaceNameQueriesMatchNormalizedActiveNameIndex() throws Exception {
        assertNormalizedActiveNameQuery(
                SpringDataSpaceRepository.class.getMethod(
                        "existsActiveByNormalizedName",
                        String.class
                )
        );
        assertNormalizedActiveNameQuery(
                SpringDataSpaceRepository.class.getMethod(
                        "existsActiveByNormalizedNameAndIdNot",
                        String.class,
                        Long.class
                )
        );
    }

    @Test
    void activeOccupancyQueryExcludesEndedAndExpiredOccupancies()
            throws Exception {
        Method listQueryMethod =
                SpringDataRoomOccupancyRepository.class.getMethod(
                "findAllActiveBySpaceIds",
                List.class,
                OffsetDateTime.class
        );
        String listQuery = listQueryMethod.getAnnotation(Query.class).value();

        assertThat(listQuery)
                .contains("JOIN learning_service.cohort_memberships")
                .contains("occupier_membership.id = ro.occupier_membership_id")
                .contains("occupier_membership.cohort_id")
                .contains("ro.status = 'ACTIVE'")
                .contains("ro.ended_at IS NULL")
                .contains("ro.expires_at > :now");

        Method existsQueryMethod =
                SpringDataRoomOccupancyRepository.class.getMethod(
                        "existsActiveBySpaceId",
                        Long.class,
                        OffsetDateTime.class
                );
        Query existsQuery = existsQueryMethod.getAnnotation(Query.class);

        assertThat(existsQuery.nativeQuery()).isTrue();
        assertThat(existsQuery.value())
                .contains("SELECT EXISTS")
                .contains("ro.space_id = :spaceId")
                .contains("ro.status = 'ACTIVE'")
                .contains("ro.ended_at IS NULL")
                .contains("ro.expires_at > :now");
    }

    private void assertNormalizedActiveNameQuery(
            Method method
    ) {
        Query annotation = method.getAnnotation(Query.class);

        assertThat(annotation.nativeQuery()).isTrue();
        assertThat(annotation.value())
                .contains("LOWER(BTRIM(s.name)) = LOWER(BTRIM(:name))")
                .contains("s.deleted_at IS NULL");
    }
}
