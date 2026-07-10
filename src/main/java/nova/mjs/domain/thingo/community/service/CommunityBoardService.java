package nova.mjs.domain.thingo.community.service;

import nova.mjs.domain.thingo.community.DTO.CommunityBoardRequest;
import nova.mjs.domain.thingo.community.DTO.CommunityBoardResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * 커뮤니티 게시판 서비스 인터페이스
 *
 * 게시판의 CRUD 및 조회 기능 정의
 *
 * - 비즈니스 로직에 대한 표준 인터페이스 제공
 * - 테스트, 확장, 대체 구현체 작성에 유리
 */
public interface CommunityBoardService {

    // 게시판 페이지네이션 조회 (+ 카테고리 필터)
    Page<CommunityBoardResponse.SummaryDTO> getBoards(Pageable pageable, String email, String communityCategoryRaw);

    // 게시판 상세 조회
    CommunityBoardResponse.DetailDTO getBoardDetail(UUID uuid, String email);

    // 게시판 생성
    CommunityBoardResponse.DetailDTO createBoard(CommunityBoardRequest request, String emailId);

    // 게시판 업데이트
    CommunityBoardResponse.DetailDTO updateBoard(UUID uuid, CommunityBoardRequest request, String email);

    // 게시판 삭제
    void deleteBoard(UUID uuid, String email);

    // HOT 게시판 조회 (페이지/사이즈는 프론트 지정, 기본 size=7)
    List<CommunityBoardResponse.SummaryDTO> getHotBoards(Pageable pageable, String email);
}
