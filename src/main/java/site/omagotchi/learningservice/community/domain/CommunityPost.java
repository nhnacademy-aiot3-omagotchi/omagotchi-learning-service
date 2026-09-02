package site.omagotchi.learningservice.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 커뮤니티 게시글. 언제나 특정 기수에 속한다.
 *
 * <p>공지와 자유글은 {@link CommunityPostType}으로만 갈린다. 둘 다 기수 게시판 안의
 * 글이고, 작성/수정 권한과 고정 가능 여부에서 차이가 난다.</p>
 */
@Entity
@Table(name = "community_posts", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityPostType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;

    // 게시글은 기수를 옮겨 다니지 않는다.
    @Column(name = "cohort_id", nullable = false, updatable = false)
    private Long cohortId;

    @Column(nullable = false)
    private boolean pinned;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static CommunityPost create(
            CommunityPostType type,
            String title,
            String content,
            UUID authorUserId,
            Long cohortId
    ) {
        CommunityPost post = new CommunityPost();
        post.type = requireType(type);
        post.title = requireTitle(title);
        post.content = requireContent(content);
        post.authorUserId = requireAuthorUserId(authorUserId);
        post.cohortId = requireCohortId(cohortId);
        post.pinned = false;
        return post;
    }

    public void update(String title, String content) {
        requireActive();
        this.title = requireTitle(title);
        this.content = requireContent(content);
    }

    public void delete(Instant deletedAt) {
        requireActive();
        if (deletedAt == null) {
            throw new IllegalArgumentException("삭제 시각은 필수입니다.");
        }
        this.deletedAt = deletedAt;
    }

    public void changePinned(boolean pinned) {
        requireActive();
        this.pinned = pinned;
    }

    public boolean isNotice() {
        return type == CommunityPostType.NOTICE;
    }

    public boolean isFree() {
        return type == CommunityPostType.FREE;
    }

    public boolean belongsTo(Long cohortId) {
        return this.cohortId.equals(cohortId);
    }

    public boolean isAuthor(UUID userId) {
        return authorUserId.equals(userId);
    }

    private static CommunityPostType requireType(CommunityPostType type) {
        if (type == null) {
            throw new IllegalArgumentException("게시글 유형은 필수입니다.");
        }
        return type;
    }

    private static String requireTitle(String title) {
        if (title == null) {
            throw new IllegalArgumentException("게시글 제목은 필수입니다.");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.isEmpty() || normalizedTitle.length() > 100) {
            throw new IllegalArgumentException("게시글 제목은 1~100자여야 합니다.");
        }
        return normalizedTitle;
    }

    private static String requireContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("게시글 내용은 필수입니다.");
        }
        String normalizedContent = content.trim();
        if (normalizedContent.isEmpty() || normalizedContent.length() > 10_000) {
            throw new IllegalArgumentException("게시글 내용은 1~10000자여야 합니다.");
        }
        return normalizedContent;
    }

    private static UUID requireAuthorUserId(UUID authorUserId) {
        if (authorUserId == null) {
            throw new IllegalArgumentException("작성자 식별자는 필수입니다.");
        }
        return authorUserId;
    }

    private static Long requireCohortId(Long cohortId) {
        if (cohortId == null) {
            throw new IllegalArgumentException("기수 식별자는 필수입니다.");
        }
        return cohortId;
    }

    private void requireActive() {
        if (deletedAt != null) {
            throw new IllegalStateException("삭제된 게시글은 변경할 수 없습니다.");
        }
    }
}
