package nova.mjs.domain.thingo.ElasticSearch.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.ElasticSearch.indexing.Preprocessor.notice.NoticeContentPreprocessor;
import nova.mjs.domain.thingo.notice.entity.Notice;
import nova.mjs.domain.thingo.ElasticSearch.SearchType;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoticeDocument implements SearchDocument{

    @Id
    private String id;

    private String title;

    private String content;

    private Instant date;

    private String link;

    private String category;

    private String type;

    @Override
    public String getType() {
        return SearchType.NOTICE.name();
    }

     @Override
    public Instant getInstant() {
        return date;
    }

    public static NoticeDocument from(
            Notice notice,
            NoticeContentPreprocessor preprocessor) {
        return NoticeDocument.builder()
                .id(notice.getId().toString())
                .title(notice.getTitle())
                .content(preprocessor.normalize(notice.getContent()))
                .date(notice.getDate().atZone(ZoneId.systemDefault()).toInstant())
                .link(notice.getLink())
                .category(notice.getCategory())
                .type(SearchType.NOTICE.name())
                .build();
    }
}