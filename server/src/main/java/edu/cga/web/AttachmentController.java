package edu.cga.web;

import java.util.List;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.Attachment;
import edu.cga.security.CurrentUser;
import edu.cga.service.AttachmentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 材料附件（P1 第三批 3.1）：上传、列表、下载。
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** POST /api/attachments（multipart：file、kind）：学生本人上传，返回附件元数据 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Attachment> upload(@RequestPart(value = "file", required = false) MultipartFile file,
                                          @RequestParam(value = "kind", required = false) String kind,
                                          @AuthenticationPrincipal CurrentUser user) {
        Attachment a = attachmentService.upload(file, kind, user);
        return ApiResponse.ok(a, Attachment.LANG_CERT.equals(a.getKind())
                ? "语言证明已上传，等待国际生办核验" : "材料已上传");
    }

    /** GET /api/attachments?ownerId=&applicationId=&kind=：可见范围内的附件元数据 */
    @GetMapping
    public ApiResponse<List<Attachment>> list(@RequestParam(required = false) String ownerId,
                                              @RequestParam(required = false) String applicationId,
                                              @RequestParam(required = false) String kind,
                                              @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(attachmentService.list(ownerId, applicationId, kind, user));
    }

    /** GET /api/attachments/{id}：下载；范围外 404 */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        AttachmentService.StoredFile f = attachmentService.load(id, user);
        Attachment a = f.attachment();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .contentLength(a.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(a.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(f.path()));
    }
}
