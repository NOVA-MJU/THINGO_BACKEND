package nova.mjs.domain.thingo.ElasticSearch.Document;

import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.calendar.entity.MjuCalendar;
import nova.mjs.domain.thingo.ElasticSearch.SearchType;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;


@Document(indexName = "mju_calendar_index")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MjuCalendarDocument implements SearchDocument  {

    @Id
    private String id;

    private String title;

    private String content;

    private String type;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant date;

    // 유효 마감(학사일정 종료일). null 가능.
    private Instant endDate;

    @Override
    public String getType() {
        return SearchType.MJU_CALENDAR.name();
    }

     @Override
    public Instant getInstant() {
        return date;
    }

    @Override
    public Instant getValidUntil() {
        return endDate;
    }

    public static MjuCalendarDocument from(MjuCalendar mjuCalendar) {
        return MjuCalendarDocument.builder()
                .id(mjuCalendar.getId().toString())
                .title(mjuCalendar.getDescription())
                .content("")
                .date(mjuCalendar.getStartDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
                .endDate(mjuCalendar.getEndDate() == null ? null
                        : mjuCalendar.getEndDate().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant())
                .type(SearchType.MJU_CALENDAR.name())
                .build();
    }
}
