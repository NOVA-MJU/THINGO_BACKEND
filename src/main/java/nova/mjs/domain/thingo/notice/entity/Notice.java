package nova.mjs.domain.thingo.notice.entity;

import jakarta.persistence.*;
import lombok.*;
import nova.mjs.domain.thingo.ElasticSearch.EntityListner.NoticeEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notice")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(NoticeEntityListener.class)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;       // 공지 제목

    @Column(columnDefinition = "TEXT")
    private String content;   // 공지 내용

    @Column(nullable = false)
    private LocalDateTime date; // 공지 날짜

    @Column(nullable = false)
    private String category;    // 공지 카테고리

    @Column(nullable = false, length = 1000)
    private String link;        // 공지 링크

    private Integer viewCount;  // 공지 조회수

    public static Notice createNotice(String title, String content, LocalDateTime date, String type, String link, Integer viewCount) {
        return Notice.builder()
                .title(title)
                .content(content)
                .date(date)
                .category(type)
                .link(link)
                .viewCount(viewCount)
                .build();
    }

}

