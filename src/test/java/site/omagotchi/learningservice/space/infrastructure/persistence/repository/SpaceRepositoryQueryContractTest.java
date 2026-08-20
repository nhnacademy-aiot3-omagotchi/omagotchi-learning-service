package site.omagotchi.learningservice.space.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

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
