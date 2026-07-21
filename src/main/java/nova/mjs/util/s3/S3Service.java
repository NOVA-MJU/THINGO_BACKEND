package nova.mjs.util.s3;

import nova.mjs.util.s3.dto.S3PresignDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * S3ServiceImpl 인터페이스
 *
 * S3에 대한 공통 기능(업로드, 복사, 삭제, 조회)을 정의합니다.
 * 다양한 도메인(회원, 커뮤니티, 이벤트 등)에서 재사용할 수 있도록 표준화된 메서드들을 제공합니다.
 */
public interface S3Service {

    /**
     * S3에 파일을 업로드하고, 업로드된 파일의 CloudFront URL을 반환합니다.
     *
     * @param file   업로드할 파일 (MultipartFile)
     * @param domainType 도메인 타입
     * @return 업로드된 파일의 CloudFront URL (예: https://cdn.example.com/profiles/{UUID}/abc123.png)
     * @throws IOException 파일 읽기/업로드 중 오류 발생 시
     */
    String uploadFile(MultipartFile file, S3DomainType domainType) throws IOException;

    /**
     * 프리사인 PUT URL을 발급합니다(영상 등 대용량 직접 업로드용).
     * 서버는 바이트를 거치지 않고, 클라이언트가 반환된 uploadUrl로 S3에 직접 PUT합니다.
     *
     * @param request 도메인/Content-Type/파일크기
     * @return uploadUrl(프리사인 PUT) + fileUrl(최종 CloudFront URL) + 만료시간
     */
    S3PresignDto.Response presignPut(S3PresignDto.Request request);

    /**
     * 지정한 S3 Key의 객체 존재 여부를 확인합니다.
     *
     * @param key S3 객체의 Key
     * @return 객체가 존재하면 true, 없으면 false
     */
    boolean doesObjectExist(String key);

    /**
     * S3에서 파일을 복사합니다.
     *
     * @param oldKey 원본 객체의 Key
     * @param newKey 복사 대상 객체의 Key
     */
    void copyFile(String oldKey, String newKey);

    /**
     * S3에서 파일을 삭제합니다.
     *
     * @param key 삭제할 객체의 Key
     */
    void deleteFile(String key);

    /**
     * 특정 폴더(prefix) 내의 모든 파일을 삭제합니다.
     *
     * @param prefix 삭제할 폴더의 prefix
     */
    void deleteFolder(String prefix);

    /**
     * CloudFront URL로부터 S3 Key를 추출합니다.
     *
     * @param imageUrl CloudFront 기반의 전체 이미지 URL
     * @return S3 Key (예: profiles/{UUID}/abc123.png)
     */
    String replaceCloudfrontUrlToS3Url(String imageUrl);

    /**
     * 특정 prefix 하위에 있는 모든 S3 Key 목록을 조회합니다.
     *
     * @param prefix 조회할 폴더 prefix
     * @return 해당 prefix 하위의 Key 목록
     */
    List<String> listKeys(String prefix);

    /**
     * 특정 prefix 하위에 있는 모든 S3 Key 목록을 조회합니다.
     *
     * @param fromPrefix 현재 폴더 위치 + 파일명
     * @param toPrefix 변경하고 싶은 폴더 위치 + 파일 명
     * @return 해당 prefix 하위의 Key 목록
     */
    void moveFolder(String fromPrefix, String toPrefix);
}
