package nova.mjs.domain.thingo.community.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nova.mjs.domain.thingo.community.DTO.BoardsQueryResult;
import nova.mjs.domain.thingo.community.DTO.CommunityBoardRequest;
import nova.mjs.domain.thingo.community.DTO.CommunityBoardResponse;
import nova.mjs.domain.thingo.community.comment.repository.CommentRepository;
import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.community.entity.enumList.CommunityCategory;
import nova.mjs.domain.thingo.community.exception.CommunityNotFoundException;
import nova.mjs.domain.thingo.community.likes.repository.CommunityLikeRepository;
import nova.mjs.domain.thingo.community.repository.CommunityBoardRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.util.s3.S3DomainType;
import nova.mjs.util.s3.S3Service;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * CommunityBoardServiceImpl
 *
 * 목표
 * - 목록/검색에서 count 쿼리 제거 (DB 집계 컬럼 likeCount/commentCount 사용)
 * - 인기글+일반글 병합 + liked 여부 제공
 *
 * 전제
 * - 좋아요/댓글 생성/삭제 시점에 CommunityBoard.likeCount/commentCount를 원자적으로 증감한다.
 *   (즉, 조회 시 COUNT 쿼리를 치지 않고도 값이 맞아야 한다)
 */
@Service
@RequiredArgsConstructor
@Log4j2
@Transactional(readOnly = true)
public class CommunityBoardServiceImpl implements CommunityBoardService {

    private final CommunityBoardRepository communityBoardRepository;
    private final CommunityLikeRepository communityLikeRepository;
    private final CommentRepository commentRepository; // 조회에서는 직접 count 쿼리를 쓰지 않지만, 기존 의존성 유지
    private final MemberRepository memberRepository;
    private final MemberQueryService memberQueryService;
    private final S3Service s3Service;

    private final String boardPostPrefix = S3DomainType.COMMUNITY_POST.getPrefix();

    /**
     * 게시글 목록 조회
     *
     * - 인기글(top3) + 일반글(page) 병합
     * - totalElements는 "일반글" 기준 (인기글은 헤더 블록 성격)
     * - likeCount/commentCount는 DB 집계 컬럼 기반으로 DTO에 넣는다.
     */
    @Override
    public Page<CommunityBoardResponse.SummaryDTO> getBoards(Pageable pageable, String email, String communityCategoryRaw) {

        BoardsQueryResult boardQueryResult = loadBoardsQueryResult(pageable, email, communityCategoryRaw);

        List<CommunityBoardResponse.SummaryDTO> popularDTOs =
                toSummaryDTOs(boardQueryResult.popularBoards(), boardQueryResult.likedUuids(), true);

        List<CommunityBoardResponse.SummaryDTO> generalDTOs =
                toSummaryDTOs(boardQueryResult.generalBoardsPage().getContent(), boardQueryResult.likedUuids(), false);

        List<CommunityBoardResponse.SummaryDTO> merged = new ArrayList<>(popularDTOs.size() + generalDTOs.size());
        merged.addAll(popularDTOs);
        merged.addAll(generalDTOs);

        // 정렬: popular 먼저, 일반글은 pageable sort(createdAt) 유지
        Comparator<CommunityBoardResponse.SummaryDTO> createdAtCmp =
                Comparator.comparing(CommunityBoardResponse.SummaryDTO::getCreatedAt);

        Sort.Order createdOrder = pageable.getSort().getOrderFor("createdAt");
        if (createdOrder != null && createdOrder.isDescending()) {
            createdAtCmp = createdAtCmp.reversed();
        }

        merged.sort(
                Comparator.<CommunityBoardResponse.SummaryDTO, Boolean>comparing(CommunityBoardResponse.SummaryDTO::isPopular)
                        .reversed()
                        .thenComparing(dto -> dto.isPopular() ? 0 : 1)
                        .thenComparing(createdAtCmp)
        );

        long totalElements = boardQueryResult.generalBoardsPage().getTotalElements();
        return new PageImpl<>(merged, pageable, totalElements);
    }

    /**
     * DB 왕복 전용
     *
     * 역할
     * - 인기글 top3
     * - 인기글 제외 일반글 페이지네이션
     * - 첫 페이지 보정(인기글 수만큼 일반글 size 조정)
     * - 로그인 유저의 좋아요 누른 글 UUID 셋
     *
     * 주의
     * - 기존에는 여기서 Like/Comment를 group by count해서 DTO를 채웠지만,
     *   이제는 CommunityBoard 집계 컬럼(likeCount/commentCount)을 신뢰하는 구조로 바꾼다.
     * - 좋아요/댓글 이벤트 시점에 집계 컬럼이 정확히 유지되어야 한다.
     */
    @Transactional(readOnly = true)
    protected BoardsQueryResult loadBoardsQueryResult(
            Pageable pageable,
            String email,
            String communityCategoryRaw
    ) {
        LocalDateTime twoWeeksAgo = LocalDateTime.now().minusWeeks(2);

        CommunityCategory categoryFilter = parseCategoryOrNull(communityCategoryRaw);

        List<CommunityBoard> popularBoards = (categoryFilter == null)
                ? communityBoardRepository.findTop3PopularBoards(twoWeeksAgo, PageRequest.of(0, 3))
                : communityBoardRepository.findTop3PopularBoardsByCategory(twoWeeksAgo, categoryFilter, PageRequest.of(0, 3));

        List<UUID> popularUuids = popularBoards.stream()
                .map(CommunityBoard::getUuid)
                .toList();

        Pageable generalPageable = buildGeneralBoardsPageable(pageable, popularBoards.size());

        // 첫 페이지가 아니면 HOT 블록 자체를 비워서 응답 content에 절대 노출되지 않게 한다.
        // (정책: HOT 상단 노출은 0페이지에서만 허용)
        List<CommunityBoard> popularBoardsForResponse = pageable.getPageNumber() == 0
                ? popularBoards
                : Collections.emptyList();

        Page<CommunityBoard> generalBoardsPage;
        if (categoryFilter == null) {
            generalBoardsPage = popularUuids.isEmpty()
                    ? communityBoardRepository.findAllWithAuthor(generalPageable)
                    : communityBoardRepository.findAllWithAuthorExcluding(popularUuids, generalPageable);
        } else {
            generalBoardsPage = popularUuids.isEmpty()
                    ? communityBoardRepository.findAllWithAuthorByCategory(categoryFilter, generalPageable)
                    : communityBoardRepository.findAllWithAuthorByCategoryExcluding(categoryFilter, popularUuids, generalPageable);
        }

        // liked 여부 조회(로그인 사용자만)
        List<UUID> allUuids = Stream.concat(popularBoards.stream(), generalBoardsPage.getContent().stream())
                .map(CommunityBoard::getUuid)
                .toList();

        Set<UUID> likedUuids = findLikedUuids(email, allUuids);

        return new BoardsQueryResult(
                popularBoardsForResponse,
                generalBoardsPage,
                likedUuids,
                Map.of(),
                Map.of()
        );
    }

    private CommunityCategory parseCategoryOrNull(String raw) {
        if (raw == null || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return CommunityCategory.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * HOT 3개를 0페이지에만 끼워 넣는 정책을 지키기 위해,
     * 일반글 조회의 offset/limit를 별도로 계산한다.
     *
     * 예시(size=10, hotCount=3)
     * - page=0: 일반글 7개 조회(0~6)
     * - page=1: 일반글 10개 조회(7~16)
     * - page=2: 일반글 10개 조회(17~26)
     */
    private Pageable buildGeneralBoardsPageable(Pageable requestPageable, int hotBoardCount) {
        long requestOffset = requestPageable.getOffset();
        int requestSize = requestPageable.getPageSize();

        // 0페이지에는 HOT가 먼저 차지하므로, 일반글은 남은 칸만 가져온다.
        if (requestPageable.getPageNumber() == 0) {
            int firstPageGeneralSize = Math.max(0, requestSize - hotBoardCount);
            return PageRequest.of(0, firstPageGeneralSize, requestPageable.getSort());
        }

        // 1페이지부터는 HOT 노출이 없으므로, 합성 목록 기준 offset에서 HOT 개수만큼 차감한다.
        long adjustedGeneralOffset = Math.max(0, requestOffset - hotBoardCount);
        return new OffsetLimitPageable(adjustedGeneralOffset, requestSize, requestPageable.getSort());
    }

    /**
     * PageRequest는 pageNumber 기반이라 "offset=7, size=10" 같은 케이스를 표현할 수 없어서,
     * HOT/일반글 합성 페이지네이션을 정확히 맞추기 위한 offset 기반 Pageable 구현을 둔다.
     */
    private static class OffsetLimitPageable implements Pageable {
        private final long offset;
        private final int pageSize;
        private final Sort sort;

        private OffsetLimitPageable(long offset, int pageSize, Sort sort) {
            if (offset < 0) {
                throw new IllegalArgumentException("offset은 0 이상이어야 합니다.");
            }
            if (pageSize < 1) {
                throw new IllegalArgumentException("pageSize는 1 이상이어야 합니다.");
            }
            this.offset = offset;
            this.pageSize = pageSize;
            this.sort = sort == null ? Sort.unsorted() : sort;
        }

        @Override
        public int getPageNumber() {
            return (int) (offset / pageSize);
        }

        @Override
        public int getPageSize() {
            return pageSize;
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Sort getSort() {
            return sort;
        }

        @Override
        public Pageable next() {
            return new OffsetLimitPageable(offset + pageSize, pageSize, sort);
        }

        @Override
        public Pageable previousOrFirst() {
            if (!hasPrevious()) {
                return first();
            }
            return new OffsetLimitPageable(offset - pageSize, pageSize, sort);
        }

        @Override
        public Pageable first() {
            return new OffsetLimitPageable(0, pageSize, sort);
        }

        @Override
        public Pageable withPage(int pageNumber) {
            if (pageNumber < 0) {
                throw new IllegalArgumentException("pageNumber는 0 이상이어야 합니다.");
            }
            return new OffsetLimitPageable((long) pageNumber * pageSize, pageSize, sort);
        }

        @Override
        public boolean hasPrevious() {
            return offset > 0;
        }
    }

    private Set<UUID> findLikedUuids(String email, List<UUID> boardUuids) {
        if (email == null || boardUuids.isEmpty()) {
            return Collections.emptySet();
        }
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) {
            return Collections.emptySet();
        }
        List<UUID> liked = communityLikeRepository.findCommunityUuidsLikedByMember(member, boardUuids);
        return new HashSet<>(liked);
    }

    /**
     * 엔티티 → Summary DTO
     *
     * - likeCount/commentCount는 CommunityBoard 집계 컬럼을 그대로 사용한다.
     * - isLiked는 likedUuids 집합 기반
     */
    private List<CommunityBoardResponse.SummaryDTO> toSummaryDTOs(
            List<CommunityBoard> boards,
            Set<UUID> likedUuids,
            boolean popular
    ) {
        return boards.stream()
                .map(b -> CommunityBoardResponse.SummaryDTO.fromEntityPreview(
                        b,
                        b.getLikeCount(),
                        b.getCommentCount(),
                        likedUuids.contains(b.getUuid()),
                        popular
                ))
                .toList();
    }

    /**
     * 게시글 상세 조회
     *
     * 원칙
     * - 상세에서도 likeCount/commentCount를 COUNT로 매번 계산하지 않는다.
     * - 집계 컬럼을 신뢰한다.
     *
     * 예외
     * - "절대 정확"이 필요한 요구가 생기면 별도 API로 분리(예: /boards/{id}/stats)
     */
    @Override
    public CommunityBoardResponse.DetailDTO getBoardDetail(UUID uuid, String email) {
        CommunityBoard board = getExistingBoard(uuid);

        int likeCount = board.getLikeCount();
        int commentCount = board.getCommentCount();

        if (email == null) {
            return CommunityBoardResponse.DetailDTO.fromEntity(
                    board,
                    likeCount,
                    commentCount,
                    false,
                    false,
                    false
            );
        }

        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) {
            return CommunityBoardResponse.DetailDTO.fromEntity(
                    board,
                    likeCount,
                    commentCount,
                    false,
                    false,
                    false
            );
        }

        boolean isLiked = communityLikeRepository.findByMemberAndCommunityBoard(member, board).isPresent();
        boolean canEdit = canEdit(board, member);
        boolean canDelete = canDelete(board, member);

        log.debug("게시글 상세 조회: uuid={}, likeCount={}, commentCount={}, isLiked={}, canEdit={}, canDelete={}",
                uuid, likeCount, commentCount, isLiked, canEdit, canDelete);

        return CommunityBoardResponse.DetailDTO.fromEntity(
                board,
                likeCount,
                commentCount,
                isLiked,
                canEdit,
                canDelete
        );
    }

    @Override
    @Transactional
    public CommunityBoardResponse.DetailDTO createBoard(CommunityBoardRequest request, String emailId) {
        Member author = memberQueryService.getMemberByEmail(emailId);

        boolean published = request.getPublished() == null || request.getPublished();
        CommunityCategory category = (request.getCommunityCategory() != null)
                ? request.getCommunityCategory()
                : CommunityCategory.FREE;

        CommunityBoard board = CommunityBoard.create(
                request.getTitle(),
                request.getContent(),
                request.getContentPreview(),
                category,
                published,
                author
        );

        communityBoardRepository.save(board);

        // 생성 직후 like/comment는 0을 그대로 사용
        return CommunityBoardResponse.DetailDTO.fromEntity(
                board,
                0,
                0,
                false
        );
    }

    @Override
    @Transactional
    public CommunityBoardResponse.DetailDTO updateBoard(UUID boardUuid, CommunityBoardRequest request, String emailId) {
        CommunityBoard board = getExistingBoard(boardUuid);

        Member member = memberQueryService.getMemberByEmail(emailId);
        if (!board.getAuthor().equals(member)) {
            throw new IllegalArgumentException("작성자만 수정할 수 있습니다.");
        }

        board.update(
                request.getTitle(),
                request.getContent(),
                request.getContentPreview(),
                request.getCommunityCategory(),
                request.getPublished()
        );

        boolean isLiked = communityLikeRepository.findByMemberAndCommunityBoard(member, board).isPresent();

        return CommunityBoardResponse.DetailDTO.fromEntity(
                board,
                board.getLikeCount(),
                board.getCommentCount(),
                isLiked
        );
    }

    @Override
    @Transactional
    public void deleteBoard(UUID uuid, String emailId) {
        CommunityBoard board = getExistingBoard(uuid);

        Member member = memberQueryService.getMemberByEmail(emailId);
        if (!canDelete(board, member)) {
            throw new IllegalArgumentException("삭제 권한이 없습니다. (작성자 또는 관리자만 삭제 가능)");
        }

        communityBoardRepository.delete(board);

        String postFolder = boardPostPrefix + board.getUuid() + "/";
        s3Service.deleteFolder(postFolder);

        log.debug("게시글 삭제 성공. uuid={}, requester={}", uuid, emailId);
    }

    @Override
    public List<CommunityBoardResponse.SummaryDTO> getHotBoards() {
        PageRequest pageRequest = PageRequest.of(0, 2);
        List<CommunityBoard> hotBoards = communityBoardRepository.findHotBoards(pageRequest);

        List<CommunityBoardResponse.SummaryDTO> result = new ArrayList<>();
        for (CommunityBoard board : hotBoards) {
            result.add(CommunityBoardResponse.SummaryDTO.fromEntityPreview(
                    board,
                    board.getLikeCount(),
                    board.getCommentCount(),
                    false,
                    true
            ));
        }

        return result;
    }

    private CommunityBoard getExistingBoard(UUID uuid) {
        return communityBoardRepository.findByUuid(uuid)
                .orElseThrow(CommunityNotFoundException::new);
    }

    /**
     * 비로그인 사용자용 fallback (유지 필요하면 남기되, COUNT 호출은 제거)
     */
    private Page<CommunityBoardResponse.SummaryDTO> mapBoardsWithoutLogin(Page<CommunityBoard> boardPage) {
        return boardPage.map(board ->
                CommunityBoardResponse.SummaryDTO.fromEntityPreview(
                        board,
                        board.getLikeCount(),
                        board.getCommentCount(),
                        false
                )
        );
    }

    private boolean canDelete(CommunityBoard board, Member member) {
        return Objects.equals(board.getAuthor(), member)
                || Member.Role.OPERATOR.equals(member.getRole());
    }

    private boolean canEdit(CommunityBoard board, Member member) {
        return Objects.equals(board.getAuthor(), member);
    }
}
