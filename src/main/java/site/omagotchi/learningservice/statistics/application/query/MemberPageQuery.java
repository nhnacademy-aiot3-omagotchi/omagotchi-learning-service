package site.omagotchi.learningservice.statistics.application.query;

import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import java.util.Arrays;

// 코드의 역할?
public record MemberPageQuery(
        WindowQuery window,
        int page,
        int size,
        SortField sortField,
        SortDirection sortDirection
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "periodStudySeconds,desc";

    public MemberPageQuery {
        if (window == null || page < 0 || size < 1 || size > MAX_SIZE
                || sortField == null || sortDirection == null) {
            throw invalidRequest();
        }
    }

    public static MemberPageQuery of(
            String requestedWindow,
            Integer requestedPage,
            Integer requestedSize,
            String requestedSort
    ) {
        int page = requestedPage == null ? DEFAULT_PAGE : requestedPage;
        int size = requestedSize == null ? DEFAULT_SIZE : requestedSize;
        String sort = requestedSort == null ? DEFAULT_SORT : requestedSort;
        String[] sortParts = sort.split(",", -1);
        if (sortParts.length != 2) {
            throw invalidRequest();
        }

        return new MemberPageQuery(
                WindowQuery.parse(requestedWindow),
                page,
                size,
                SortField.from(sortParts[0]),
                SortDirection.from(sortParts[1])
        );
    }

    public long offset() {
        return (long) page * size;
    }

    public enum SortField {
        PERIOD_STUDY_SECONDS("periodStudySeconds"),
        TODAY_STUDY_SECONDS("todayStudySeconds"),
        ACTIVE_STUDY_DAYS("activeStudyDays"),
        RECORD_COUNT("recordCount"),
        LAST_STUDIED_AT("lastStudiedAt"),
        COHORT_MEMBERSHIP_ID("cohortMembershipId");

        private final String value;

        SortField(String value) {
            this.value = value;
        }

        private static SortField from(String value) {
            return Arrays.stream(values())
                    .filter(field -> field.value.equals(value))
                    .findFirst()
                    .orElseThrow(MemberPageQuery::invalidRequest);
        }
    }

    public enum SortDirection {
        ASC,
        DESC;

        private static SortDirection from(String value) {
            return switch (value) {
                case "asc" -> ASC;
                case "desc" -> DESC;
                default -> throw invalidRequest();
            };
        }
    }

    private static BusinessException invalidRequest() {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
}
