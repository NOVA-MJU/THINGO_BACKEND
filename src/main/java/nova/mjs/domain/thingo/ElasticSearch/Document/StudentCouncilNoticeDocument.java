package nova.mjs.domain.thingo.ElasticSearch.Document;

import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.department.entity.Department;
import nova.mjs.domain.thingo.department.entity.StudentCouncilNotice;
import nova.mjs.domain.thingo.ElasticSearch.SearchType;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentCouncilNoticeDocument implements SearchDocument{

    @Id
    private String id;

    private String title;

    private String content;

    private String department;

    private Instant date;

    private String type;

    @Override
    public String getType() {
        return SearchType.STUDENT_COUNCIL_NOTICE.name();
    }

     @Override
    public Instant getInstant() {
        return date;
    }

    public static StudentCouncilNoticeDocument from(StudentCouncilNotice notice) {

        String safeContent = nullToEmpty(notice.getContent());
        String derivedTitle = deriveTitle(notice.getTitle(), safeContent);

        return StudentCouncilNoticeDocument.builder()
                .id(notice.getUuid().toString())
                .title(derivedTitle)
                .content(safeContent)
                .department(resolveDepartmentLabel(notice.getDepartment()))
                .date(notice.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .type(SearchType.STUDENT_COUNCIL_NOTICE.name())
                .build();
    }

    private static String resolveDepartmentLabel(Department department) {
        if (department == null || department.getDepartmentName() == null) {
            return null;
        }
        return department.getDepartmentName().getLabel();
    }


    /* 제목 생성 규칙 */
    private static String deriveTitle(String title, String content) {
        if (title != null && !title.isBlank()) {return title;}

        if (content == null || content.isBlank()) {return "(제목 없음)";}

        int limit = Math.min(40, content.length());
        return content.substring(0, limit);
    }

    private static String nullToEmpty(String str) {
        return str == null ? "" : str;
    }

}
