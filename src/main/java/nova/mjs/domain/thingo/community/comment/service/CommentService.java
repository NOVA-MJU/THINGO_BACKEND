package nova.mjs.domain.thingo.community.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nova.mjs.domain.thingo.ElasticSearch.indexing.update.SearchIndexUpdateService;
import nova.mjs.domain.thingo.block.service.BlockQueryService;
import nova.mjs.domain.thingo.community.comment.DTO.CommentResponseDto;
import nova.mjs.domain.thingo.community.comment.entity.Comment;
import nova.mjs.domain.thingo.community.comment.exception.CommentNotFoundException;
import nova.mjs.domain.thingo.community.comment.exception.CommentReplyDepthException;
import nova.mjs.domain.thingo.community.comment.likes.repository.CommentLikeRepository;
import nova.mjs.domain.thingo.community.comment.repository.CommentRepository;
import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.community.exception.CommunityNotFoundException;
import nova.mjs.domain.thingo.community.repository.CommunityBoardRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;
import nova.mjs.domain.thingo.report.service.ReportQueryService;
import nova.mjs.util.profanity.ProfanityFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
@Service
@RequiredArgsConstructor
@Log4j2
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final CommunityBoardRepository communityBoardRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final SearchIndexUpdateService searchIndexUpdateService;
    private final BlockQueryService blockQueryService;
    private final ReportQueryService reportQueryService;
    private final ProfanityFilter profanityFilter;

    public List<CommentResponseDto.CommentSummaryDto> getCommentsByBoard(
            UUID boardUuid,
            String email
    ) {
        CommunityBoard board = getExistingBoard(boardUuid);

        List<Comment> allComments = commentRepository.findByCommunityBoard(board);
        if (allComments.isEmpty()) {
            return List.of();
        }

        List<Comment> topLevelComments = allComments.stream()
                .filter(c -> c.getParent() == null)
                .toList();

        Member me = null;
        Set<UUID> likedSet = Collections.emptySet();
        Set<Long> hiddenMemberIds = Collections.emptySet();
        Set<UUID> selfReportedUuids = Collections.emptySet();

        if (email != null) {
            me = memberRepository.findByEmail(email).orElse(null);
            if (me != null) {
                // 차단(양방향) 사용자 작성 댓글은 숨긴다
                hiddenMemberIds = blockQueryService.getHiddenMemberIds(me.getId());
                // 내가 신고한 댓글은 L2 임계 도달 전이라도 즉시 숨긴다(자가 숨김)
                selfReportedUuids = reportQueryService.getSelfReportedTargetUuids(me.getId(), ReportTargetType.COMMENT);

                List<UUID> commentUuids =
                        allComments.stream().map(Comment::getUuid).toList();
                likedSet = commentLikeRepository.findByMemberAndComment_UuidIn(me, commentUuids).stream()
                        .map(commentLike -> commentLike.getComment().getUuid())
                        .collect(java.util.stream.Collectors.toSet());
            }
        }

        Member finalMe = me;
        Set<UUID> finalLikedSet = likedSet;
        Set<Long> finalHiddenMemberIds = hiddenMemberIds;
        Set<UUID> finalSelfReportedUuids = selfReportedUuids;

        return topLevelComments.stream()
                // 신고 누적 자동 숨김(L2) 댓글은 스레드째 숨김
                .filter(comment -> !comment.isHidden())
                // 차단 사용자가 작성한 최상위 댓글(스레드)은 통째로 숨김
                .filter(comment -> !finalHiddenMemberIds.contains(comment.getMember().getId()))
                // 내가 신고한 최상위 댓글(스레드)은 즉시 숨김
                .filter(comment -> !finalSelfReportedUuids.contains(comment.getUuid()))
                .map(comment ->
                        CommentResponseDto.CommentSummaryDto.fromEntityWithReplies(
                                comment,
                                finalLikedSet.contains(comment.getUuid()),
                                finalLikedSet,
                                finalMe,
                                finalHiddenMemberIds,
                                finalSelfReportedUuids
                        )
                )
                .toList();
    }

    @Transactional
    public CommentResponseDto.CommentSummaryDto createComment(
            UUID boardUuid,
            String content,
            String email
    ) {
        Member member = getExistingMember(email);
        CommunityBoard board = getExistingBoard(boardUuid);

        // 비속어 마스킹(L1): 저장 직전 댓글 내용의 명백한 욕설을 *로 치환한다
        Comment saved = commentRepository.save(
                Comment.create(board, member, profanityFilter.mask(content))
        );

        communityBoardRepository.increaseCommentCount(boardUuid);
        syncCommentCountToSearch(boardUuid);

        log.debug(
                "댓글 작성 성공. boardUuid={}, commentUuid={}, writer={}",
                boardUuid, saved.getUuid(), email
        );

        return CommentResponseDto.CommentSummaryDto.fromEntity(saved, true);
    }

    @Transactional
    public CommentResponseDto.CommentSummaryDto createReply(
            UUID parentCommentUuid,
            String content,
            String email
    ) {
        Comment parent = getExistingComment(parentCommentUuid);

        if (parent.getParent() != null) {
            throw new CommentReplyDepthException();
        }

        Member member = getExistingMember(email);

        // 비속어 마스킹(L1): 대댓글 내용도 저장 직전 마스킹한다
        Comment saved = commentRepository.save(
                Comment.createReply(parent, member, profanityFilter.mask(content))
        );

        UUID boardUuid = parent.getCommunityBoard().getUuid();
        communityBoardRepository.increaseCommentCount(boardUuid);
        syncCommentCountToSearch(boardUuid);

        log.debug(
                "대댓글 작성 성공. boardUuid={}, parentUuid={}, replyUuid={}, writer={}",
                boardUuid, parentCommentUuid, saved.getUuid(), email
        );

        return CommentResponseDto.CommentSummaryDto.fromEntity(saved, true);
    }

    @Transactional
    public void deleteCommentByUuid(UUID commentUuid, String email) {
        Comment comment = getExistingComment(commentUuid);

        deleteComment(comment, email);
    }

    private void deleteComment(Comment comment, String email) {
        UUID boardUuid = comment.getCommunityBoard().getUuid();

        if (!Objects.equals(comment.getMember().getEmail(), email)) {
            throw new IllegalArgumentException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        int deletedRows = 1;
        UUID commentUuid = comment.getUuid();

        if (comment.getParent() == null) {
            deletedRows += commentRepository.countByParent_Uuid(commentUuid);
        }

        commentRepository.delete(comment);

        communityBoardRepository.decreaseCommentCountBy(boardUuid, deletedRows);
        syncCommentCountToSearch(boardUuid);

        log.debug(
                "댓글 스레드 삭제 성공. boardUuid={}, rootCommentUuid={}, deletedRows={}",
                boardUuid, commentUuid, deletedRows
        );
    }

    /**
     * 댓글 수 변경에 따른 검색 인덱스 동기화
     *
     * - 파생 데이터이므로 서비스에서 직접 처리
     * - 트랜잭션 커밋 이후에만 ES 반영
     */
    private void syncCommentCountToSearch(UUID boardUuid) {
        int newCommentCount =
                communityBoardRepository.findCommentCount(boardUuid);

        searchIndexUpdateService.updateCommunityCounts(
                boardUuid,
                null,
                newCommentCount
        );
    }

    private Member getExistingMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);
    }

    private CommunityBoard getExistingBoard(UUID uuid) {
        return communityBoardRepository.findByUuid(uuid)
                .orElseThrow(CommunityNotFoundException::new);
    }

    private Comment getExistingComment(UUID uuid) {
        return commentRepository.findByUuid(uuid)
                .orElseThrow(CommentNotFoundException::new);
    }
}
