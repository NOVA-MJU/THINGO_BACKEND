package nova.mjs.domain.thingo.search.service;

import jakarta.persistence.EntityManager;
import nova.mjs.domain.thingo.ElasticSearch.indexing.Preprocessor.community.CommunityContentPreprocessor;
import nova.mjs.domain.thingo.ElasticSearch.indexing.Preprocessor.notice.NoticeContentPreprocessor;
import nova.mjs.domain.thingo.broadcast.repository.BroadcastRepository;
import nova.mjs.domain.thingo.calendar.repository.MjuCalendarRepository;
import nova.mjs.domain.thingo.community.repository.CommunityBoardRepository;
import nova.mjs.domain.thingo.department.repository.DepartmentScheduleRepository;
import nova.mjs.domain.thingo.department.repository.StudentCouncilNoticeRepository;
import nova.mjs.domain.thingo.news.repository.NewsRepository;
import nova.mjs.domain.thingo.notice.repository.NoticeRepository;
import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import nova.mjs.domain.thingo.search.academic.AcademicGuideCatalog;
import nova.mjs.domain.thingo.search.mapper.PgUnifiedSearchMapper;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgSearchIndexSyncServiceTest {

    @Mock private NoticeRepository noticeRepository;
    @Mock private NewsRepository newsRepository;
    @Mock private CommunityBoardRepository communityBoardRepository;
    @Mock private DepartmentScheduleRepository departmentScheduleRepository;
    @Mock private StudentCouncilNoticeRepository studentCouncilNoticeRepository;
    @Mock private BroadcastRepository broadcastRepository;
    @Mock private MjuCalendarRepository mjuCalendarRepository;
    @Mock private AcademicGuideCatalog academicGuideCatalog;
    @Mock private NoticeContentPreprocessor noticeContentPreprocessor;
    @Mock private CommunityContentPreprocessor communityContentPreprocessor;
    @Mock private UnifiedSearchIndexRepository repository;
    @Mock private PgUnifiedSearchMapper mapper;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private PgSearchIndexSyncService service;

    @BeforeEach
    void setUpEmptySourcePages() {
        lenient().when(noticeRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        lenient().when(newsRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        lenient().when(communityBoardRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        lenient().when(departmentScheduleRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        lenient().when(studentCouncilNoticeRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        lenient().when(broadcastRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        lenient().when(mjuCalendarRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        lenient().when(academicGuideCatalog.documents()).thenReturn(List.of());
    }

    @Test
    @DisplayName("reconcile은 전체 findAll 대신 제한된 페이지로 읽고 누락 문서를 비활성화한다")
    void reconcileUsesBoundedPagesAndDeactivatesMissingDocuments() {
        UnifiedSearchIndex stale = UnifiedSearchIndex.of(
                "NOTICE:1", "1", "NOTICE", "NOTICE", "title", "content",
                null, null, null, 0, 0, 0.0, null, null, "tokens", "title tokens");
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stale)));

        service.reconcile();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(noticeRepository).findAll(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id").isAscending()).isTrue();
        assertThat(stale.getActive()).isFalse();
        verify(repository, never()).findAll();
        verify(repository).rebuildMissingVectors();
        verify(repository, never()).rebuildVectors();
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("전체 동기화는 게시판과 분리된 학사안내 문서를 통합 인덱스에 포함한다")
    void syncAllIncludesStaticAcademicGuideDocuments() {
        AcademicGuideCatalog.AcademicGuideDocument guide =
                new AcademicGuideCatalog.AcademicGuideDocument(
                        "2026-2:rule:test",
                        "미휴 학기교 12학점",
                        "적용 학번: 2025학번 이후",
                        "academic_rule",
                        "https://example.com/guide#47",
                        Instant.parse("2026-08-04T00:00:00Z"));
        UnifiedSearchIndex index = UnifiedSearchIndex.of(
                "ACADEMIC_GUIDE:2026-2:rule:test",
                guide.id(),
                guide.getType(),
                guide.category(),
                guide.title(),
                guide.content(),
                "명지대학교",
                guide.link(),
                null,
                null,
                null,
                0.0,
                guide.instant(),
                null,
                "학기교",
                "학기교");
        when(academicGuideCatalog.documents()).thenReturn(List.of(guide));
        when(mapper.from(guide)).thenReturn(index);

        service.syncAll();

        verify(repository).truncate();
        verify(repository).saveAll(argThat(values -> values.iterator().next().getType().equals("ACADEMIC_GUIDE")));
        verify(repository).rebuildVectors();
    }

    @Test
    @DisplayName("학사안내 전용 동기화는 다른 게시판을 읽지 않고 학사 문서만 교체한다")
    void syncAcademicGuidesOnlyReplacesAcademicDocuments() {
        AcademicGuideCatalog.AcademicGuideDocument guide =
                new AcademicGuideCatalog.AcademicGuideDocument(
                        "2026-2:rule:test",
                        "미휴 학기교 학번별 안내",
                        "2025학번 이후 12학점",
                        "academic_rule",
                        "https://example.com/guide#47",
                        Instant.parse("2026-08-04T00:00:00Z"));
        UnifiedSearchIndex index = UnifiedSearchIndex.of(
                "ACADEMIC_GUIDE:2026-2:rule:test",
                guide.id(), guide.getType(), guide.category(), guide.title(), guide.content(),
                "명지대학교", guide.link(), null, null, null, 0.0, guide.instant(), null,
                "학기교", "학기교");
        when(academicGuideCatalog.documents()).thenReturn(List.of(guide));
        when(mapper.from(guide)).thenReturn(index);

        service.syncAcademicGuides();

        verify(repository).deleteAllByType("ACADEMIC_GUIDE");
        verify(repository).saveAll(argThat(values -> values.iterator().next().getType().equals("ACADEMIC_GUIDE")));
        verify(repository).rebuildVectorsByType("ACADEMIC_GUIDE");
        verify(repository, never()).truncate();
        verify(noticeRepository, never()).findAll(any(Pageable.class));
    }
}
