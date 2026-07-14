package nova.mjs.domain.thingo.community.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nova.mjs.domain.thingo.community.DTO.CommunityBoardResponse;
import nova.mjs.domain.thingo.community.comment.DTO.CommentResponseDto;
import nova.mjs.domain.thingo.community.comment.entity.Comment;
import nova.mjs.domain.thingo.community.comment.exception.CommentNotFoundException;
import nova.mjs.domain.thingo.community.comment.repository.CommentRepository;
import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.community.exception.CommunityNotFoundException;
import nova.mjs.domain.thingo.community.repository.CommunityBoardRepository;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;
import nova.mjs.domain.thingo.report.service.ContentModerationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 신고 기반 콘텐츠 숨김/복원 (L2).
 *
 * 역할
 * - 신고 도메인의 {@link ContentModerationPort} 구현: 임계 초과 대상 자동 숨김
 * - 운영자용 숨김 목록 조회 + 숨김 해제(복원)
 *
 * 도메인 규칙
 * - 신고 도메인은 이 클래스를 인터페이스(포트)로만 참조한다(엔티티 직접 결합 방지).
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class CommunityModerationService implements ContentModerationPort {

    private final CommunityBoardRepository communityBoardRepository;
    private final CommentRepository commentRepository;

    /**
     * 신고 누적 임계 초과 대상 자동 숨김 (신고 트랜잭션 내에서 호출됨).
     * - 이미 숨김이거나 대상이 없으면 무시(멱등).
     * - REVIEW(명지도 리뷰)는 ReviewModerationService가 담당하므로 여기선 무시한다.
     */
    @Override
    @Transactional
    public void hideByReport(ReportTargetType targetType, UUID targetUuid) {
        switch (targetType) {
            case BOARD -> communityBoardRepository.findByUuid(targetUuid)
                    .ifPresent(CommunityBoard::hideByReport);
            case COMMENT -> commentRepository.findByUuid(targetUuid)
                    .ifPresent(Comment::hideByReport);
            case REVIEW -> { /* 리뷰는 ReviewModerationService(ContentModerationPort)가 처리 */ }
        }
    }

    /**
     * 자동 숨김된 게시글 목록 조회 (운영자 검토 큐).
     */
    @Transactional(readOnly = true)
    public List<CommunityBoardResponse.SummaryDTO> getHiddenBoards() {
        return communityBoardRepository.findByHiddenTrueOrderByCreatedAtDesc().stream()
                .map(board -> CommunityBoardResponse.SummaryDTO.fromEntityPreview(
                        board,
                        board.getLikeCount(),
                        board.getCommentCount(),
                        false,
                        false
                ))
                .toList();
    }

    /**
     * 자동 숨김된 댓글 목록 조회 (운영자 검토 큐).
     */
    @Transactional(readOnly = true)
    public List<CommentResponseDto.CommentSummaryDto> getHiddenComments() {
        return commentRepository.findByHiddenTrueOrderByCreatedAtDesc().stream()
                .map(comment -> CommentResponseDto.CommentSummaryDto.fromEntity(comment, false))
                .toList();
    }

    /**
     * 게시글 숨김 해제 (운영자 검토 후 정상 복원).
     */
    @Transactional
    public void restoreBoard(UUID boardUuid) {
        CommunityBoard board = communityBoardRepository.findByUuid(boardUuid)
                .orElseThrow(CommunityNotFoundException::new);
        board.restore();
        log.info("게시글 숨김 해제 - uuid: {}", boardUuid);
    }

    /**
     * 댓글 숨김 해제 (운영자 검토 후 정상 복원).
     */
    @Transactional
    public void restoreComment(UUID commentUuid) {
        Comment comment = commentRepository.findByUuid(commentUuid)
                .orElseThrow(CommentNotFoundException::new);
        comment.restore();
        log.info("댓글 숨김 해제 - uuid: {}", commentUuid);
    }
}
