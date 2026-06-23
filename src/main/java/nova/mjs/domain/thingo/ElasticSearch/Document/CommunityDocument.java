package nova.mjs.domain.thingo.ElasticSearch.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.ElasticSearch.indexing.Preprocessor.community.CommunityContentPreprocessor;
import nova.mjs.domain.thingo.ElasticSearch.SearchType;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Elasticsearch 색인을 위한 Community 문서
 *
 * 설계 원칙:
 * - content는 검색용 평문만 저장한다.
 * - 에디터 JSON 원문은 Elasticsearch에 저장하지 않는다.
 * - 전처리는 Document 생성 시점에 완료된다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityDocument implements SearchDocument {

    @Id
    private String id;

    private String title;

    /**
     * 검색 전용 content
     * - CommunityContentPreprocessor를 통해 정규화된 텍스트만 저장
     */
    private String content;

    private Instant date;

    private String type;

    private String category;

    private String link;

    private Integer likeCount;
    private Integer commentCount;



    @Override
    public String getType() {
        return SearchType.COMMUNITY.name();
    }

    @Override
    public Instant getInstant() {
        return date;
    }

    @Override
    public Integer getLikeCount() {
        return likeCount;
    }

    @Override
    public Integer getCommentCount() {
        return commentCount;
    }

    /**
     * CommunityBoard → CommunityDocument 변환
     *
     * - content는 Editor JSON을 전처리하여 검색용 텍스트로 변환
     * - publishedAt이 null일 수 있으므로 날짜 변환 시 방어
     */
    public static CommunityDocument from(
            CommunityBoard board,
            CommunityContentPreprocessor preprocessor
    ) {
        return CommunityDocument.builder()
                .id(board.getUuid().toString())
                .title(board.getTitle())
                .content(preprocessor.normalize(board.getContent()))
                .date(board.getPublishedAt() != null
                        ? board.getPublishedAt()
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        : null)
                .type(SearchType.COMMUNITY.name())
                .category(board.getCategory().name())
                .likeCount(board.getLikeCount())
                .commentCount(board.getCommentCount())
                .build();
    }
}
