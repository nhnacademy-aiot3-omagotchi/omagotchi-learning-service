package site.omagotchi.learningservice.community.application.port;

import site.omagotchi.learningservice.community.domain.CommunityPostAttachment;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 첨부파일 영속화 경계.
 *
 * <p>Application 은 첨부파일을 읽고 지우는 일만 알면 된다. Spring Data 타입을 직접 쓰면
 * 저장 기술이 바뀔 때 Application 이 함께 흔들리므로, 필요한 연산만 여기 남긴다.</p>
 */
public interface CommunityPostAttachmentPort {

    /** 게시글의 첨부파일. 화면에 보이는 순서(displayOrder, id)로 돌려준다. */
    List<CommunityPostAttachment> findByPostId(Long postId);

    Optional<CommunityPostAttachment> findByIdAndPostId(Long attachmentId, Long postId);

    /**
     * 첨부파일을 저장하고 즉시 반영한다.
     *
     * <p>같은 트랜잭션 안에서 저장된 식별자와 제약 위반을 바로 알아야 하므로 지연시키지 않는다.</p>
     */
    List<CommunityPostAttachment> saveAll(List<CommunityPostAttachment> attachments);

    void delete(CommunityPostAttachment attachment);

    void deleteByPostId(Long postId);
}
