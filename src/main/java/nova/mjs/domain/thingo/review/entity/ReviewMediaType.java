package nova.mjs.domain.thingo.review.entity;

/**
 * 리뷰 미디어 종류. 사진(IMAGE) 또는 영상(VIDEO).
 * 영상은 프리사인 직접 업로드로 저장되며, URL은 IMAGE와 동일하게 CloudFront 주소다.
 */
public enum ReviewMediaType {
    IMAGE,
    VIDEO
}
