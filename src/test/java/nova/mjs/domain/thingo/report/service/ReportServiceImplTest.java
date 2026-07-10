package nova.mjs.domain.thingo.report.service;

import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.report.dto.ReportDTO;
import nova.mjs.domain.thingo.report.entity.Report;
import nova.mjs.domain.thingo.report.entity.ReportReason;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;
import nova.mjs.domain.thingo.report.event.ReportCreatedEvent;
import nova.mjs.domain.thingo.report.exception.InvalidReportDetailException;
import nova.mjs.domain.thingo.report.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReportServiceImpl reportService;

    private static final String EMAIL = "reporter@mju.ac.kr";

    private Member 신고자() {
        return Member.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .email(EMAIL)
                .name("홍길동")
                .nickname("길동")
                .build();
    }

    private ReportDTO.Request.Create 요청(ReportTargetType targetType, ReportReason reason, String etcDetail) {
        ReportDTO.Request.Create request = new ReportDTO.Request.Create();
        ReflectionTestUtils.setField(request, "targetType", targetType);
        ReflectionTestUtils.setField(request, "targetUuid", UUID.randomUUID());
        ReflectionTestUtils.setField(request, "reason", reason);
        ReflectionTestUtils.setField(request, "etcDetail", etcDetail);
        return request;
    }

    @Test
    @DisplayName("신고를 저장하고 메일 발송 이벤트를 발행한다")
    void should_저장하고_이벤트발행_when_정상신고() {
        // given
        Member reporter = 신고자();
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(reporter));
        given(reportRepository.save(any(Report.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReportDTO.Request.Create request = 요청(ReportTargetType.BOARD, ReportReason.ABUSE, null);

        // when
        ReportDTO.Response.Detail response = reportService.createReport(EMAIL, request);

        // then
        assertThat(response.getReason()).isEqualTo(ReportReason.ABUSE);
        assertThat(response.getReasonLabel()).isEqualTo("욕설/비하/인신공격");
        verify(reportRepository).save(any(Report.class));
        verify(eventPublisher).publishEvent(any(ReportCreatedEvent.class));
    }

    @Test
    @DisplayName("기타 사유인데 상세 내용이 비어 있으면 예외를 던진다")
    void should_throwException_when_기타사유_상세없음() {
        // given
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(신고자()));
        ReportDTO.Request.Create request = 요청(ReportTargetType.COMMENT, ReportReason.ETC, "   ");

        // when & then
        assertThatThrownBy(() -> reportService.createReport(EMAIL, request))
                .isInstanceOf(InvalidReportDetailException.class);
        verify(reportRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("기타 사유가 아니면 상세 내용은 저장하지 않는다")
    void should_상세무시_when_기타사유아님() {
        // given
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(신고자()));
        given(reportRepository.save(any(Report.class))).willAnswer(invocation -> invocation.getArgument(0));
        ReportDTO.Request.Create request = 요청(ReportTargetType.BOARD, ReportReason.COMMERCIAL_AD, "버려질 내용");

        // when
        reportService.createReport(EMAIL, request);

        // then
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getEtcDetail()).isNull();
    }

    @Test
    @DisplayName("신고자를 찾을 수 없으면 예외를 던진다")
    void should_throwException_when_신고자없음() {
        // given
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        ReportDTO.Request.Create request = 요청(ReportTargetType.BOARD, ReportReason.ABUSE, null);

        // when & then
        assertThatThrownBy(() -> reportService.createReport(EMAIL, request))
                .isInstanceOf(MemberNotFoundException.class);
        verify(reportRepository, never()).save(any());
    }
}
