package nova.mjs.util.s3;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.s3.dto.S3PresignDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3ServiceImpl s3Service;

    // 1. 게시글 작성 시 사용할 tempUUID  발급
    @GetMapping("/temp-uuid")
    public ResponseEntity<ApiResponse<String>> generateTempUuid() {
        return ResponseEntity.ok(ApiResponse.success(UUID.randomUUID().toString()));
    }

    /**
     * 범용 S3 업로드 엔드포인트
     *
     * @param file 업로드할 파일
     * @param domain 업로드 도메인 (enum name)
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @RequestParam MultipartFile file,
            @RequestParam("domain") S3DomainType domain) throws IOException {

        String url = s3Service.uploadFile(file, domain);
        return ResponseEntity.ok(ApiResponse.success(url));
    }

    /**
     * 프리사인 PUT URL 발급(영상 등 대용량 직접 업로드).
     * 클라이언트는 uploadUrl로 S3에 직접 PUT하고, fileUrl을 리뷰 작성 요청에 담는다.
     */
    @PostMapping("/presign")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<S3PresignDto.Response>> presign(
            @Valid @RequestBody S3PresignDto.Request request) {
        return ResponseEntity.ok(ApiResponse.success(s3Service.presignPut(request)));
    }
}
