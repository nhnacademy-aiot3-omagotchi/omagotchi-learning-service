package site.omagotchi.learningservice.community.application.port;

import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;

import java.util.Optional;

public interface CommunityPostQueryPort {

    CommunityPostPage findVisiblePosts(CommunityPostSearchCondition condition);

    Optional<CommunityPostDetail> findVisiblePost(Long cohortId, Long postId);
}
