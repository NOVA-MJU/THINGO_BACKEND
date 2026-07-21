package nova.mjs.util.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.util.exception.request.RequestException;
import nova.mjs.util.s3.dto.S3PresignDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S3ServiceImpl
 *
 * AWS S3에 대한 파일 업로드, 복사, 삭제, 조회 등 주요 기능을 제공하는 서비스 클래스입니다.
 * CloudFront와 연계하여 업로드된 파일의 접근 URL을 반환하며,
 * S3 객체의 키(Key) 관리와 관련된 다양한 유틸리티 메서드를 포함하고 있습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.cloudfront.url}")
    private String cloudFrontUrl;

    /** 프리사인 업로드 상한(50MB) 및 유효시간(5분) */
    private static final long MAX_PRESIGN_SIZE = 50L * 1024 * 1024;
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

    /** 허용 Content-Type → 확장자. 영상(mp4/mov/webm) + 이미지(jpg/png/webp) */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "video/mp4", ".mp4",
            "video/quicktime", ".mov",
            "video/webm", ".webm",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    /**
     * S3에 파일 업로드 (파일명은 SHA-256 해시 기반으로 생성)
     *
     * @param file 업로드할 파일
     * @param domainType 도메인 타입
     * @return 업로드된 파일의 CloudFront URL
     * @throws IOException
     */
    @Override
    public String uploadFile(MultipartFile file, S3DomainType domainType) throws IOException {
        String fileUrl = S3KeyGenerator.generateFileKeyWithHash(file, domainType);

        log.info("[S3 업로드 요청] key: {}", fileUrl);


        // 이미 존재하는지 확인
        if (!doesObjectExist(fileUrl)) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileUrl)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("[S3 업로드 완료] key: {}", fileUrl);
        } else {
            log.info("[S3에 동일한 파일 존재. 업로드 생략] key: {}", fileUrl);
        }

        return cloudFrontUrl + "/" + fileUrl;
    }

    /**
     * 프리사인 PUT URL 발급. Content-Type 허용 목록/용량 검증 후
     * {prefix}{uuid}{ext} 키로 5분짜리 PUT URL을 만든다. 서버는 바이트를 거치지 않는다.
     */
    @Override
    public S3PresignDto.Response presignPut(S3PresignDto.Request request) {
        // 1. Content-Type 허용 검증
        String extension = EXTENSION_BY_CONTENT_TYPE.get(request.getContentType());
        if (extension == null) {
            throw new RequestException(ErrorCode.S3_PRESIGN_UNSUPPORTED_TYPE);
        }
        // 2. 용량 검증(0 이하/상한 초과 거부)
        if (request.getFileSize() <= 0 || request.getFileSize() > MAX_PRESIGN_SIZE) {
            throw new RequestException(ErrorCode.S3_PRESIGN_SIZE_EXCEEDED);
        }

        // 3. 키 생성 + 프리사인 PUT 발급
        String key = request.getDomain().getPrefix() + UUID.randomUUID() + extension;
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(request.getContentType())
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        log.info("[S3 프리사인 발급] key: {}", key);

        return S3PresignDto.Response.builder()
                .uploadUrl(presigned.url().toString())
                .fileUrl(cloudFrontUrl + "/" + key)
                .expiresInSeconds(PRESIGN_TTL.getSeconds())
                .build();
    }

    @Override
    public boolean doesObjectExist(String key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    // 전체 폴더 이동
    @Override
    public void moveFolder(String fromPrefix, String toPrefix) {
        List<String> keys = listKeys(fromPrefix);
        for (String oldKey : keys) {
            String newKey = oldKey.replace(fromPrefix, toPrefix);
            copyFile(oldKey, newKey);
            deleteFile(oldKey);
        }
    }

    @Override
    public List<String> listKeys(String prefix) {
        log.info("[S3 Key 목록 조회] prefix: {}", prefix);
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        List<String> keys = response.contents().stream()
                .map(S3Object::key)
                .toList();

        log.info("[S3 Key 목록 조회 완료] prefix: {}, keys: {}", prefix, keys);

        return keys;
    }


    @Override
    public void copyFile(String oldKey, String newKey) {
        log.info("[S3 복사 시작] from: {}, to: {}", oldKey, newKey);

        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(oldKey)
                .destinationBucket(bucket)
                .destinationKey(newKey)
                .build();

        s3Client.copyObject(copyRequest);

        log.info("[S3 복사 완료] to: {}", newKey);
    }

    @Override
    public void deleteFile(String key) {
        log.info("[S3 삭제 요청] key: {}", key);

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try {
            s3Client.deleteObject(deleteRequest);
            log.info("[S3 삭제 완료] key: {}", key);
        } catch (S3Exception e) {
            log.error("[S3 삭제 실패] key: {}, message: {}", key, e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    @Override
    public void deleteFolder(String prefix) {
        log.info("[S3 폴더 삭제 시작] prefix: {}", prefix);
        List<String> keys = listKeys(prefix);
        log.info("[S3 폴더 내 파일 수]: {}", keys.size());

        for (String key : keys) {
            deleteFile(key);
        }

        log.info("[S3 폴더 삭제 완료] prefix: {}", prefix);
    }

    @Override
    public String replaceCloudfrontUrlToS3Url(String imageUrl) {
        log.info("[CloudFront URL → S3 key 변환 요청] imageUrl: {}", imageUrl);
        if (!imageUrl.startsWith(cloudFrontUrl)) {
            throw new IllegalArgumentException("Invalid CloudFront URL: " + imageUrl);
        }
        String key = imageUrl.replace(cloudFrontUrl + "/", "");
        log.info("[변환된 S3 key]: {}", key);

        return key;
    }


}
