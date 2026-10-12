package nova.mjs.domain.thingo.search.academic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 배포 산출물에 포함된 학사안내 자료를 통합검색 문서로 제공한다.
 *
 * 공지와 달리 적용 학번·단과대·학과·원문 페이지가 검색 의미를 결정하므로
 * 별도 ACADEMIC_GUIDE 타입으로 유지하되, 동일한 통합검색 인덱스에 적재한다.
 */
@Component
public class AcademicGuideCatalog {

    private static final String RESOURCE_PATH = "academic/academic_guide_2026_2.json";
    private static final String TYPE = "ACADEMIC_GUIDE";
    private static final int INDEX_ID_MAX_LENGTH = 64;

    private final List<AcademicGuideDocument> documents;

    public AcademicGuideCatalog(ObjectMapper objectMapper) {
        this.documents = load(objectMapper);
    }

    public List<AcademicGuideDocument> documents() {
        return documents;
    }

    private List<AcademicGuideDocument> load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            CatalogFile file = objectMapper.readValue(input, CatalogFile.class);
            if (file.documents() == null || file.documents().isEmpty()) {
                throw new IllegalStateException("Academic guide has no documents: " + RESOURCE_PATH);
            }
            if (file.documentCount() != file.documents().size()) {
                throw new IllegalStateException("Academic guide document count mismatch: " + RESOURCE_PATH);
            }
            return file.documents().stream()
                    .map(entry -> AcademicGuideDocument.from(entry, file.publishedAt(), file.sourceTitle()))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load academic guide: " + RESOURCE_PATH, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogFile(int documentCount,
                               Instant publishedAt,
                               String sourceTitle,
                               List<CatalogEntry> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogEntry(String id,
                                String title,
                                String category,
                                Integer documentPage,
                                Integer admissionYearFrom,
                                Integer admissionYearTo,
                                List<String> colleges,
                                List<String> departments,
                                List<String> keywords,
                                String content,
                                String sourceUrl) {
    }

    public record AcademicGuideDocument(String id,
                                        String title,
                                        String content,
                                        String category,
                                        String link,
                                        Instant instant) implements SearchDocument {

        // sourceTitle: 학사안내문이 첨부된 공지 제목. AI 요약이 출처를 "공지 제목 · 첨부파일 N쪽"으로 보여줄 수 있게
        // 본문 앞 메타데이터에 '출처 공지'로 넣는다 (AI 서버는 검색 결과의 제목·본문만 받음)
        private static AcademicGuideDocument from(CatalogEntry entry, Instant publishedAt, String sourceTitle) {
            String indexId = TYPE + ":" + entry.id();
            if (indexId.length() > INDEX_ID_MAX_LENGTH) {
                throw new IllegalStateException(
                        "Academic guide index id exceeds " + INDEX_ID_MAX_LENGTH + " chars: " + indexId);
            }
            String metadata = List.of(
                            formatYear(entry.admissionYearFrom(), entry.admissionYearTo()),
                            formatList("단과대", entry.colleges()),
                            formatList("학과·전공", entry.departments()),
                            entry.documentPage() == null ? "" : "학사안내문 " + entry.documentPage() + "쪽",
                            sourceTitle == null || sourceTitle.isBlank() ? "" : "출처 공지: " + sourceTitle,
                            formatList("검색어", entry.keywords()))
                    .stream()
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining(" | "));
            String searchableContent = metadata.isBlank()
                    ? entry.content()
                    : metadata + "\n" + entry.content();
            return new AcademicGuideDocument(
                    entry.id(),
                    entry.title(),
                    searchableContent,
                    entry.category(),
                    entry.sourceUrl(),
                    publishedAt);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getContent() {
            return content;
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public Instant getInstant() {
            return instant;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public String getLink() {
            return link;
        }

        @Override
        public String getAuthorName() {
            return "명지대학교";
        }

        private static String formatYear(Integer from, Integer to) {
            if (from == null && to == null) return "";
            if (from != null && to == null) return "적용 학번: " + from + "학번 이후";
            if (from == null) return "적용 학번: " + to + "학번까지";
            if (from.equals(to)) return "적용 학번: " + from + "학번";
            return "적용 학번: " + from + "~" + to + "학번";
        }

        private static String formatList(String label, List<String> values) {
            if (values == null || values.isEmpty()) return "";
            return label + ": " + String.join(", ", values);
        }
    }
}
