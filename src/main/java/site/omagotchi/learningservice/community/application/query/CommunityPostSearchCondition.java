package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.util.UUID;

public record CommunityPostSearchCondition(
        UUID userId,
        int page,
        int size,
        CommunityPostType type,
        String search
) {
}
