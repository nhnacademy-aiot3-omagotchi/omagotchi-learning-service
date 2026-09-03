package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPostType;

/**
 * 커뮤니티 목록 조회 조건.
 *
 * <p>가시성은 userId가 아니라 cohortId로 판정한다. 소속 검증은 조회 진입에서
 * 이미 끝나므로, 이 조건은 "어느 기수 게시판을 보는가"만 담는다.</p>
 */
public record CommunityPostSearchCondition(
        Long cohortId,
        int page,
        int size,
        CommunityPostType type,
        String search
) {
}
