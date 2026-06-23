package nova.mjs.domain.thingo.ElasticSearch.Document;

import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.department.entity.DepartmentSchedule;
import nova.mjs.domain.thingo.ElasticSearch.SearchType;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentScheduleDocument implements SearchDocument{

    @Id
    private String id;

    private String title;

    private String content;

    private String department;

    private Instant date;

    // 유효 마감(학과일정 종료일). null 가능.
    private Instant endDate;

    private List<String> suggest;

    private String type;

    @Override
    public String getType() {
        return SearchType.DEPARTMENT_SCHEDULE.name();
    }

     @Override
    public Instant getInstant() {
        return date;
    }

    @Override
    public Instant getValidUntil() {
        return endDate;
    }

    public static DepartmentScheduleDocument from(DepartmentSchedule schedule) {
        return DepartmentScheduleDocument.builder()
                .id(schedule.getDepartmentScheduleUuid().toString())
                .title(schedule.getTitle())
                .content(schedule.getContent())
                .department(schedule.getDepartment().getDepartmentName().getLabel())
                .date(schedule.getStartDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
                .endDate(schedule.getEndDate() == null ? null
                        : schedule.getEndDate().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant())
                .type(SearchType.DEPARTMENT_SCHEDULE.name())
                .build();
    }
}
