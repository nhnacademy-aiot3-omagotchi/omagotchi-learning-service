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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityPostScope scope;

    @Column(name = "cohort_id")
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
            CommunityPostScope scope,
            Long cohortId
    ) {
        CommunityPost post = new CommunityPost();
        post.type = requireType(type);
        post.title = requireTitle(title);
        post.content = requireContent(content);
        post.authorUserId = requireAuthorUserId(authorUserId);
        post.scope = requireScope(scope);
        post.cohortId = validateCohortId(post.scope, cohortId);
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

    public boolean isGlobal() {
        return scope == CommunityPostScope.GLOBAL;
    }

    public boolean isCohortScoped() {
        return scope == CommunityPostScope.COHORT;
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

    private static CommunityPostScope requireScope(CommunityPostScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("게시글 공개 범위는 필수입니다.");
        }
        return scope;
    }

    private static Long validateCohortId(CommunityPostScope scope, Long cohortId) {
        if (scope == CommunityPostScope.GLOBAL && cohortId != null) {
            throw new IllegalArgumentException("전체 공개 게시글은 기수를 가질 수 없습니다.");
        }
        if (scope == CommunityPostScope.COHORT && cohortId == null) {
            throw new IllegalArgumentException("기수 공개 게시글은 기수가 필요합니다.");
        }
        return cohortId;
    }

    private void requireActive() {
        if (deletedAt != null) {
            throw new IllegalStateException("삭제된 게시글은 변경할 수 없습니다.");
        }
    }
}
