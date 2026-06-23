package nova.mjs.domain.thingo.ElasticSearch.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.ElasticSearch.Document.*;
import nova.mjs.domain.thingo.ElasticSearch.Repository.PostgresUnifiedSearchRepository;
import nova.mjs.domain.thingo.ElasticSearch.indexing.Preprocessor.community.CommunityContentPreprocessor;
import nova.mjs.domain.thingo.ElasticSearch.indexing.Preprocessor.notice.NoticeContentPreprocessor;
import nova.mjs.domain.thingo.ElasticSearch.indexing.mapper.UnifiedSearchMapper;
import nova.mjs.domain.thingo.broadcast.repository.BroadcastRepository;
import nova.mjs.domain.thingo.calendar.repository.MjuCalendarRepository;
import nova.mjs.domain.thingo.community.repository.CommunityBoardRepository;
import nova.mjs.domain.thingo.department.repository.DepartmentScheduleRepository;
import nova.mjs.domain.thingo.department.repository.StudentCouncilNoticeRepository;
import nova.mjs.domain.thingo.news.repository.NewsRepository;
import nova.mjs.domain.thingo.notice.repository.NoticeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexSyncService {

    private final NoticeRepository noticeRepository;
    private final NewsRepository newsRepository;
    private final CommunityBoardRepository communityBoardRepository;
    private final DepartmentScheduleRepository departmentScheduleRepository;
    private final StudentCouncilNoticeRepository studentCouncilNoticeRepository;
    private final BroadcastRepository broadcastRepository;
    private final MjuCalendarRepository mjuCalendarRepository;

    private final UnifiedSearchMapper unifiedSearchMapper;
    private final PostgresUnifiedSearchRepository postgresUnifiedSearchRepository;

    private final NoticeContentPreprocessor noticeContentPreprocessor;
    private final CommunityContentPreprocessor communityContentPreprocessor;

    public void syncAll() {
        log.info("[SEARCH][PG][SYNC][ALL] start");

        List<PostgresUnifiedSearchRepository.SearchWriteModel> rows = new ArrayList<>();

        rows.addAll(buildRows(noticeRepository.findAll(), noticeContentPreprocessor, NoticeDocument::from));
        rows.addAll(buildRows(communityBoardRepository.findAll(), communityContentPreprocessor, CommunityDocument::from));
        rows.addAll(buildRows(newsRepository.findAll(), NewsDocument::from));
        rows.addAll(buildRows(departmentScheduleRepository.findAll(), DepartmentScheduleDocument::from));
        rows.addAll(buildRows(studentCouncilNoticeRepository.findAll(), StudentCouncilNoticeDocument::from));
        rows.addAll(buildRows(broadcastRepository.findAll(), BroadcastDocument::from));
        rows.addAll(buildRows(mjuCalendarRepository.findAll(), MjuCalendarDocument::from));

        postgresUnifiedSearchRepository.rebuildSearchDocuments(rows);
        postgresUnifiedSearchRepository.refreshSearchVectors();

        log.info("[SEARCH][PG][SYNC][ALL] rows={}", rows.size());
    }

    private <E> List<PostgresUnifiedSearchRepository.SearchWriteModel> buildRows(List<E> entities,
                                                                                  Function<E, ? extends SearchDocument> mapper) {
        return entities.stream()
                .map(mapper)
                .map(unifiedSearchMapper::from)
                .map(this::toWriteModel)
                .toList();
    }

    private <E, P> List<PostgresUnifiedSearchRepository.SearchWriteModel> buildRows(List<E> entities,
                                                                                     P preprocessor,
                                                                                     BiFunction<E, P, ? extends SearchDocument> mapper) {
        return entities.stream()
                .map(entity -> mapper.apply(entity, preprocessor))
                .map(unifiedSearchMapper::from)
                .map(this::toWriteModel)
                .toList();
    }

    private PostgresUnifiedSearchRepository.SearchWriteModel toWriteModel(UnifiedSearchDocument doc) {
        return new PostgresUnifiedSearchRepository.SearchWriteModel(
                doc.getId(),
                doc.getOriginalId(),
                doc.getType(),
                doc.getTitle(),
                doc.getTitleNormalized(),
                doc.getContent(),
                doc.getContentNormalized(),
                doc.getCategory(),
                doc.getCategoryNormalized(),
                doc.getSearchTokens(),
                doc.getLink(),
                doc.getImageUrl(),
                doc.getDate(),
                doc.getUpdatedAt(),
                Boolean.TRUE.equals(doc.getActive()),
                doc.getPopularity(),
                doc.getLikeCount(),
                doc.getCommentCount(),
                doc.getAuthorName()
        );
    }
}
