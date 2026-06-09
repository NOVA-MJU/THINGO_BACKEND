package nova.mjs.domain.thingo.banner.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.banner.dto.BannerDTO;
import nova.mjs.util.entity.BaseEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 광고 배너 엔티티.
 * 운영팀이 구글 시트에서 편집한 데이터를 동기화(전체 교체)하여 적재한다.
 * 컬럼 확장 시 SyncRow + 본 엔티티에 필드만 추가하면 됨(헤더 이름 매핑 + 전체 교체 구조).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "banners")
public class Banner extends BaseEntity {

    /* PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 비즈니스 식별자 */
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    /* 제목 (필수) */
    @Column(nullable = false)
    private String title;

    /* 한줄소개 */
    @Column
    private String oneLineIntro;

    /* 이미지 URL (시트 업로드 다이얼로그가 자동 기입) */
    @Column
    private String imageUrl;

    /* 카테고리 (시트 드롭다운으로 입력 제한) */
    @Column
    private String category;

    /* 배너 클릭 시 이동 URL */
    @Column
    private String linkUrl;

    /* 노출 순서 (오름차순) */
    @Column(nullable = false)
    private int displayOrder;

    /* 노출 여부 */
    @Column(nullable = false)
    private boolean active;

    /* 노출 시작일 (null = 제한 없음) */
    @Column
    private LocalDate startAt;

    /* 노출 종료일 (null = 제한 없음) */
    @Column
    private LocalDate endAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Banner(UUID uuid, String title, String oneLineIntro, String imageUrl,
                   String category, String linkUrl, int displayOrder, boolean active,
                   LocalDate startAt, LocalDate endAt) {
        this.uuid = uuid;
        this.title = title;
        this.oneLineIntro = oneLineIntro;
        this.imageUrl = imageUrl;
        this.category = category;
        this.linkUrl = linkUrl;
        this.displayOrder = displayOrder;
        this.active = active;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /**
     * 시트 동기화 행으로부터 배너 생성.
     * 동기화는 전체 교체 방식이므로 매번 새 uuid를 부여한다.
     * 날짜는 "yyyy-MM-dd" 문자열을 파싱하며, 형식 오류 시 DateTimeParseException을 던진다(호출부에서 행 번호와 함께 처리).
     */
    public static Banner from(BannerDTO.SyncRow row) {
        return Banner.builder()
                .uuid(UUID.randomUUID())
                .title(row.getTitle())
                .oneLineIntro(row.getOneLineIntro())
                .imageUrl(row.getImageUrl())
                .category(row.getCategory())
                .linkUrl(row.getLinkUrl())
                .displayOrder(row.getDisplayOrder() == null ? 0 : row.getDisplayOrder())
                .active(row.getActive() == null || row.getActive())
                .startAt(parseDate(row.getStartAt()))
                .endAt(parseDate(row.getEndAt()))
                .build();
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value.trim());
    }

    /**
     * 현재 노출 대상인지 판정: 활성 + 노출기간 내(경계 포함).
     */
    public boolean isVisibleOn(LocalDate today) {
        if (!active) return false;
        if (startAt != null && today.isBefore(startAt)) return false;
        if (endAt != null && today.isAfter(endAt)) return false;
        return true;
    }
}
