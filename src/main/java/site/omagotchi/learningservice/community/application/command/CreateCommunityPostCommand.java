package site.omagotchi.learningservice.community.application.command;

import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

public record CreateCommunityPostCommand(
        CommunityPostType type,
        String title,
        String content,
        CommunityPostScope scope,
        Long cohortId
) {
}
