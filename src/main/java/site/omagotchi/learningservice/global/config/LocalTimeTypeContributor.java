package site.omagotchi.learningservice.global.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;

import java.sql.Types;

/**
 * 시간대 없는 {@code TIME} 컬럼을 {@link java.time.LocalTime} 그대로 읽고 쓰게 만든다.
 *
 * <p>기본 매핑은 {@code java.sql.Time}을 거치는데, 그 변환이 {@code hibernate.jdbc.time_zone}과
 * JVM 기본 시간대를 적용해 값을 통째로 이동시킨다. KST 서버에서 {@code 18:00}을 저장하면 DB에
 * {@code 09:00}이 들어가고 읽을 때 되돌아오므로, 앱끼리는 맞아 보이지만 DB 실제 값과 다른
 * 시간대의 서버에서는 어긋난다.</p>
 *
 * <p><b>애초에 환산 대상이 아니다.</b> {@code TIME}에 담기는 값은 시점이 아니라 "매일 몇 시"라는
 * 벽시계 규칙이고, 날짜가 없어 UTC 등가값이 정의되지 않는다. 해석 기준은 각 행이 함께 들고 있는
 * timezone 컬럼이며({@code cohort_attendance_policies.timezone}), 서버가 어디에 떠 있는지와는
 * 무관해야 한다.</p>
 *
 * <p>Entity마다 {@code @JdbcTypeCode(SqlTypes.LOCAL_TIME)}를 붙이는 대신 여기서 한 번 등록한다.
 * domain Class가 Hibernate에 의존하지 않고, 앞으로 추가되는 {@code TIME} 컬럼도 같은 규칙을
 * 자동으로 따른다.</p>
 *
 * <p>등록은 {@code META-INF/services/org.hibernate.boot.model.TypeContributor}의 ServiceLoader
 * 규약을 쓴다. {@code hibernate.type_contributors} 설정에 넘기는 {@code TypeContributorList}는
 * 제거 예정으로 표시돼 있다.</p>
 */
public class LocalTimeTypeContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        typeContributions.getTypeConfiguration()
                .getJdbcTypeRegistry()
                .addDescriptor(Types.TIME, LocalTimeJdbcType.INSTANCE);
    }
}
