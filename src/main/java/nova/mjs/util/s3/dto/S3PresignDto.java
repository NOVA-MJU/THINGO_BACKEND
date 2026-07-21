package nova.mjs.util.s3.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.util.s3.S3DomainType;

/**
 * 프리사인 PUT 업로드 요청/응답 DTO.
 * 클라이언트는 uploadUrl로 S3에 직접 PUT하고, fileUrl을 서버 저장용으로 사용한다.
 */
public class S3PresignDto {

    @Getter
    @NoArgsConstructor
    public static class Request {

        @NotNull
        private S3DomainType domain;

        /** MIME 타입 (예: video/mp4). 확장자·저장 Content-Type 결정에 사용 */
        @NotBlank
        private String contentType;

        /** 업로드 예정 파일 크기(byte). 상한 검증용 */
        private long fileSize;
    }

    @Getter
    @Builder
    public static class Response {
        private String uploadUrl;       // 프리사인 PUT URL (S3 직접 업로드)
        private String fileUrl;         // 최종 CloudFront URL (리뷰 저장용)
        private long expiresInSeconds;  // 프리사인 만료 시간
    }
}
