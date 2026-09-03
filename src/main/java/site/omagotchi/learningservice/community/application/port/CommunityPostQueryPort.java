package site.omagotchi.learningservice.community.application.port;

import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;

import java.util.Optional;

public interface CommunityPostQueryPort {

    /** 고정 공지는 제외한 목록을 돌려준다. */
    CommunityPostPage findVisiblePosts(CommunityPostSearchCondition condition);

    /** 기수의 고정 공지. 유형·검색어와 무관하게 조회한다. */
    Optional<CommunityPostListItem> findPinnedPost(Long cohortId);

    Optional<CommunityPostDetail> findVisiblePost(Long cohortId, Long postId);
}
