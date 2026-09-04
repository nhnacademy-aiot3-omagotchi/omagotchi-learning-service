package site.omagotchi.learningservice.community.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.community.domain.CommunityPostAttachment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityPostAttachmentRepository extends JpaRepository<CommunityPostAttachment, Long> {

    List<CommunityPostAttachment> findByPostIdOrderByDisplayOrderAscIdAsc(Long postId);

    List<CommunityPostAttachment> findByPostIdInOrderByPostIdAscDisplayOrderAscIdAsc(Collection<Long> postIds);

    Optional<CommunityPostAttachment> findByIdAndPostId(Long attachmentId, Long postId);

    void deleteByPostId(Long postId);
}
