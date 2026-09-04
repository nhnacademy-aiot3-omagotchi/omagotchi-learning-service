package site.omagotchi.learningservice.community.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import site.omagotchi.learningservice.community.application.CommunityPostCommandService;
import site.omagotchi.learningservice.community.application.CommunityPostQueryService;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.presentation.request.CreateCommunityPostRequest;
import site.omagotchi.learningservice.community.presentation.request.PinCommunityPostRequest;
import site.omagotchi.learningservice.community.presentation.request.UpdateCommunityPostRequest;
import site.omagotchi.learningservice.community.presentation.response.CommunityPostDetailResponse;
import site.omagotchi.learningservice.community.presentation.response.CommunityPostPageResponse;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 기수 커뮤니티 게시글 REST API다.
 *
 * <p>게시판은 기수에 속하므로 소속 기수를 경로에서 받는다. 그 기수의 ACTIVE 소속이
 * 아니면 기수 존재를 숨기기 위해 404로 끊는다. 공지와 자유글은 같은 게시판 안에서
 * type으로 갈리고, 권한 차이는 기수 membership role로 판정한다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohort-id}/community/posts")
public class CommunityPostController {

    private final CommunityPostQueryService communityPostQueryService;
    private final CommunityPostCommandService communityPostCommandService;

    @GetMapping
    public CommunityPostPageResponse getPosts(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) CommunityPostType type,
            @RequestParam(required = false) String search
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostPageResponse.from(communityPostQueryService.getPosts(
                user.userId(),
                cohortId,
                page,
                size,
                type,
                search
        ));
    }

    @GetMapping("/{post-id}")
    public CommunityPostDetailResponse getPost(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("post-id") Long postId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostQueryService.getPost(
                user.userId(),
                cohortId,
                postId
        ));
    }

    @GetMapping("/{post-id}/attachments/{attachment-id}")
    public ResponseEntity<Resource> downloadAttachment(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("post-id") Long postId,
            @PathVariable("attachment-id") Long attachmentId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        var download = communityPostQueryService.downloadAttachment(
                user.userId(), cohortId, postId, attachmentId
        );
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.originalFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    @DeleteMapping("/{post-id}/attachments/{attachment-id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("post-id") Long postId,
            @PathVariable("attachment-id") Long attachmentId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        communityPostCommandService.deleteAttachment(user.userId(), cohortId, postId, attachmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityPostDetailResponse create(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @Valid @RequestBody CreateCommunityPostRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostCommandService.create(
                user.userId(),
                cohortId,
                request.toCommand()
        ));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityPostDetailResponse createWithAttachments(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @Valid @RequestPart("post") CreateCommunityPostRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostCommandService.create(
                user.userId(),
                cohortId,
                request.toCommand(attachmentFiles(attachments))
        ));
    }

    @PatchMapping("/{post-id}")
    public CommunityPostDetailResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("post-id") Long postId,
            @Valid @RequestBody UpdateCommunityPostRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostCommandService.update(
                user.userId(),
                cohortId,
                postId,
                request.toCommand()
        ));
    }

    @PatchMapping(
            path = "/{post-id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public CommunityPostDetailResponse updateWithAttachments(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("post-id") Long postId,
            @Valid @RequestPart("post") UpdateCommunityPostRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostCommandService.update(
                user.userId(),
                cohortId,
                postId,
                attachments == null ? request.toCommand() : request.toCommand(attachmentFiles(attachments))
        ));
    }

    @DeleteMapping("/{post-id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("post-id") Long postId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        communityPostCommandService.delete(user.userId(), cohortId, postId);
    }

    @PatchMapping("/{post-id}/pin")
    public CommunityPostDetailResponse pin(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("post-id") Long postId,
            @Valid @RequestBody PinCommunityPostRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostCommandService.pin(
                user.userId(),
                cohortId,
                postId,
                request.toCommand()
        ));
    }

    private List<CommunityAttachmentFile> attachmentFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return IntStream.range(0, files.size())
                .mapToObj(index -> {
                    MultipartFile file = files.get(index);
                    return new CommunityAttachmentFile(
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            index,
                            file::getInputStream
                    );
                })
                .toList();
    }
}
