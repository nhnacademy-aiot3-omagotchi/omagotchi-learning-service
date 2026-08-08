package site.omagotchi.learningservice.community.application.command;

public record UpdateCommunityPostCommand(
        String title,
        String content
) {
}
