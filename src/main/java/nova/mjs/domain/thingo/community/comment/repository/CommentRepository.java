package nova.mjs.domain.thingo.community.comment.repository;

import nova.mjs.domain.thingo.community.comment.entity.Comment;
import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CommentRepository
 *
 * 역할
 * - 게시글의 댓글 목록 조회 (페이지네이션 제거)
 * - 댓글 단건 조회(UUID)
 * - 회원 기준 댓글 조회(마이페이지)
 * - 부모 댓글 삭제 시 연관관계(cascade) 기반으로 대댓글/좋아요까지 함께 삭제
 *
 * 설계 원칙
 * - 검색/목록/상세에서 "댓글 수"는 COUNT로 매번 계산하지 않는다.
 * - commentCount는 CommunityBoard 집계 컬럼을 신뢰한다.
 * - 따라서 countByCommunityBoardUuid, group by count 류는
 *   "조회 API"에서는 사용하지 않도록 서비스에서 제거한다.
 *
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 게시글 기준 댓글 전체 조회 (페이지네이션 없음)
     * - 현재 서비스 로직에서 전체 로딩 후 트리 구성(topLevel만 필터) 방식에 필요
     */
    List<Comment> findByCommunityBoard(CommunityBoard communityBoard);

    /**
     * UUID로 댓글 단건 조회
     */
    Optional<Comment> findByUuid(UUID uuid);

    /**
     * 신고 누적으로 자동 숨김된 댓글 목록 (운영자 검토 큐, L2).
     */
    List<Comment> findByHiddenTrueOrderByCreatedAtDesc();

    /**
     * UUID + 게시글 UUID로 댓글 단건 조회
     * - 요청 경로의 boardUUID/commentUUID 일치성 검증용
     */
    Optional<Comment> findByUuidAndCommunityBoard_Uuid(UUID commentUuid, UUID boardUuid);

    /**
     * 특정 회원이 댓글을 작성한 게시물 리스트 조회 (중복 방지)
     */
    @Query("SELECT DISTINCT c.communityBoard FROM Comment c WHERE c.member = :member")
    List<CommunityBoard> findDistinctCommunityBoardByMember(@Param("member") Member member);

    /**
     * 특정 회원이 작성한 댓글 조회 (게시글 fetch join)
     * - 마이페이지 등에서 댓글과 게시글 정보가 같이 필요할 때 사용
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.communityBoard WHERE c.member = :member")
    Page<Comment> findByMember(@Param("member") Member member, Pageable pageable);

    /**
     * 특정 회원이 작성한 댓글 개수
     */
    int countByMember(Member member);

    int countByParent_Uuid(UUID parentUuid);
}
