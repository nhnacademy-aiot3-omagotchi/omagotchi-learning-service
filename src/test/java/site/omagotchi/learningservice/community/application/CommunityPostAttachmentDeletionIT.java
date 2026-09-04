package site.omagotchi.learningservice.community.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 첨부파일 삭제는 DB 커밋이 끝난 뒤에만 객체 저장소를 지운다.
 *
 * <p>단위 테스트는 Mockito 로 만든 서비스를 직접 부르므로 {@code @Transactional} 프록시가 없고,
 * 커밋 콜백도 테스트가 손으로 실행한다. 여기서는 Spring 이 관리하는 실제 트랜잭션에서
 * 커밋과 롤백을 각각 태워, 등록만이 아니라 생명주기까지 확인한다.</p>
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("첨부파일 삭제와 트랜잭션 경계")
class CommunityPostAttachmentDeletionIT {

    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String STORAGE_KEY = "2026/08/08/attachment.png";

    @Autowired
    private CommunityPostCommandService commandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private CommunityAttachmentStorage attachmentStorage;

    @MockitoBean
    private CohortAccessService cohortAccessService;

    private Long cohortId;
    private Long postId;
    private Long attachmentId;

    @BeforeEach
    void setUp() {
        cohortId = saveCohort();
        postId = savePost(cohortId);
        attachmentId = saveAttachment(postId);
        given(cohortAccessService.isActiveMember(cohortId, AUTHOR_ID)).willReturn(true);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("delete from learning_service.community_post_attachments where post_id = ?", postId);
        jdbcTemplate.update("delete from learning_service.community_posts where id = ?", postId);
        jdbcTemplate.update("delete from learning_service.cohorts where id = ?", cohortId);
    }

    @Test
    @DisplayName("커밋된 뒤에 객체 저장소에서도 지운다")
    void deletesStoredObjectAfterCommit() {
        commandService.deleteAttachment(AUTHOR_ID, cohortId, postId, attachmentId);

        // 커밋이 끝난 뒤 실행되므로, 메타데이터가 남는 상태에서 파일만 사라지는 일이 없다
        verify(attachmentStorage).delete(STORAGE_KEY);
        assertEquals(0, countAttachments());
    }

    @Test
    @DisplayName("롤백되면 객체 저장소를 건드리지 않는다")
    void keepsStoredObjectWhenTransactionRollsBack() {
        transactionTemplate.execute(status -> {
            commandService.deleteAttachment(AUTHOR_ID, cohortId, postId, attachmentId);
            // 서비스의 @Transactional 은 이 트랜잭션에 참여한다. 되돌리면 커밋 콜백도 돌지 않아야 한다
            status.setRollbackOnly();
            return null;
        });

        verify(attachmentStorage, never()).delete(any());
        // 메타데이터가 살아 있으니 파일도 남아 있어야 한다
        assertEquals(1, countAttachments());
    }

    private int countAttachments() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from learning_service.community_post_attachments where post_id = ?",
                Integer.class,
                postId
        );
        return count == null ? 0 : count;
    }

    private Long saveCohort() {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.cohorts (
                            name, description, start_date, end_date, status, created_by_user_id
                        )
                        values ('첨부 삭제 기수', '설명', '2026-08-01', '2026-08-31', 'ACTIVE', ?)
                        returning id
                        """,
                Long.class,
                AUTHOR_ID
        );
    }

    private Long savePost(Long cohortId) {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.community_posts (
                            type, title, content, author_user_id, cohort_id, pinned,
                            created_at, updated_at, deleted_at
                        )
                        values (?, '첨부 있는 글', '내용', ?, ?, false, ?, ?, null)
                        returning id
                        """,
                Long.class,
                CommunityPostType.FREE.name(),
                AUTHOR_ID,
                cohortId,
                OffsetDateTime.parse("2026-08-08T00:00:00Z"),
                OffsetDateTime.parse("2026-08-08T00:00:00Z")
        );
    }

    private Long saveAttachment(Long postId) {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.community_post_attachments (
                            post_id, storage_key, original_file_name, content_type, size_bytes, display_order
                        )
                        values (?, ?, 'attachment.png', 'image/png', 1024, 0)
                        returning id
                        """,
                Long.class,
                postId,
                STORAGE_KEY
        );
    }
}
