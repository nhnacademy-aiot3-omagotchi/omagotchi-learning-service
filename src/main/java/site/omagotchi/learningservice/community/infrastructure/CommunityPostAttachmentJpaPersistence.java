package site.omagotchi.learningservice.community.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.community.application.port.CommunityPostAttachmentPort;
import site.omagotchi.learningservice.community.domain.CommunityPostAttachment;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommunityPostAttachmentJpaPersistence implements CommunityPostAttachmentPort {

    private final CommunityPostAttachmentRepository communityPostAttachmentRepository;

    @Override
    public List<CommunityPostAttachment> findByPostId(Long postId) {
        return communityPostAttachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(postId);
    }

    @Override
    public Optional<CommunityPostAttachment> findByIdAndPostId(Long attachmentId, Long postId) {
        return communityPostAttachmentRepository.findByIdAndPostId(attachmentId, postId);
    }

    @Override
    public List<CommunityPostAttachment> saveAll(List<CommunityPostAttachment> attachments) {
        return communityPostAttachmentRepository.saveAllAndFlush(attachments);
    }

    @Override
    public void delete(CommunityPostAttachment attachment) {
        communityPostAttachmentRepository.delete(attachment);
    }

    @Override
    public void deleteByPostId(Long postId) {
        communityPostAttachmentRepository.deleteByPostId(postId);
    }
}
