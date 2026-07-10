package nova.mjs.domain.thingo.block.service;

import java.util.Set;

/**
 * 타 도메인(커뮤니티/댓글 등)이 차단 숨김 처리를 위해 의존하는 조회 인터페이스.
 *
 * 도메인 간 직접 결합을 피하기 위해 Entity가 아닌 member id 집합만 노출한다.
 */
public interface BlockQueryService {

    /**
     * 뷰어 기준으로 화면에서 숨겨야 할 사용자 member id 집합.
     * (내가 차단한 사용자 + 나를 차단한 사용자, 양방향)
     *
     * @param viewerMemberId 로그인 사용자 member id. null이면 빈 집합.
     */
    Set<Long> getHiddenMemberIds(Long viewerMemberId);
}
