package nova.mjs.domain.thingo.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰에 첨부된 사진/영상 1건. 리뷰당 최대 10개, sortOrder로 표시 순서를 보존한다.
 * (바 인디케이터 N분할·좌우 슬라이드 순서가 이 값을 따른다)
 *
 * 생성은 Review.addMedia(...)를 통해서만 이뤄진다(부모 Review에 종속).
 */
@Entity
@Table(name = "map_review_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_review_media_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_review_id", nullable = false)
    private Review review;

    /** CloudFront URL (https://thingo.kr/...) */
    @Column(name = "url", nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private ReviewMediaType mediaType;

    /** 0-base 표시 순서 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder(access = AccessLevel.PRIVATE)
    private ReviewMedia(Review review, String url, ReviewMediaType mediaType, int sortOrder) {
        this.review = review;
        this.url = url;
        this.mediaType = mediaType;
        this.sortOrder = sortOrder;
    }

    /** 부모 리뷰에 부착된 미디어 생성 (Review.addMedia 내부에서만 호출) */
    static ReviewMedia of(Review review, String url, ReviewMediaType mediaType, int sortOrder) {
        return ReviewMedia.builder()
                .review(review)
                .url(url)
                .mediaType(mediaType)
                .sortOrder(sortOrder)
                .build();
    }
}
